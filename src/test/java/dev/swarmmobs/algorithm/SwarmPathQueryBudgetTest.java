package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmPathQueryBudgetTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void budgetIsSharedPerTickButRefreshesNextTick() {
        var budget = new SwarmPathQueryBudget();
        assertTrue(budget.tryReserve(A, 10, 4, 8));
        assertTrue(budget.tryReserve(B, 10, 4, 8));
        assertFalse(budget.tryReserve(C, 10, 4, 8));
        var stats = budget.snapshot(10, 8);
        assertEquals(8, stats.reservedTokens());
        assertEquals(1, stats.waiters());
        assertEquals(2, stats.reservationsGranted());
        assertEquals(1, stats.reservationsDeferred());

        assertTrue(budget.tryReserve(C, 11, 4, 8));
        assertEquals(4, budget.snapshot(11, 8).reservedTokens());
        assertEquals(0, budget.snapshot(11, 8).waiters());
    }

    @Test
    void queuedRequestTakesPriorityOverNewArrivals() {
        var budget = new SwarmPathQueryBudget();
        assertTrue(budget.tryReserve(A, 10, 6, 8));
        assertFalse(budget.tryReserve(B, 10, 4, 8));
        // C can spend only the tokens not reserved for older waiter B.
        assertTrue(budget.tryReserve(C, 11, 4, 8));
        assertEquals(1, budget.snapshot(11, 8).waiters());
        assertTrue(budget.tryReserve(B, 11, 4, 8));
        assertEquals(8, budget.snapshot(11, 8).reservedTokens());
    }

    @Test
    void activeWaitingAgentCannotBeBypassedByRepeatRequester() {
        var budget = new SwarmPathQueryBudget();
        assertTrue(budget.tryReserve(A, 7, 6, 8));
        assertFalse(budget.tryReserve(B, 7, 6, 8));
        assertFalse(budget.tryReserve(A, 8, 6, 8));
        assertTrue(budget.tryReserve(B, 8, 6, 8));
        assertFalse(budget.tryReserve(A, 8, 6, 8));
        assertTrue(budget.tryReserve(A, 9, 6, 8));
    }

    @Test
    void inactiveWaitersExpireWithoutBlockingActiveMobForever() {
        var budget = new SwarmPathQueryBudget();
        assertTrue(budget.tryReserve(A, 100, 8, 8));
        assertFalse(budget.tryReserve(B, 100, 6, 8));
        // The waiting B never tries again; old queue entry must be evicted.
        assertTrue(budget.tryReserve(C, 221, 6, 8));
        assertEquals(0, budget.snapshot(221, 8).waiters());
    }

    @Test
    void atomicEpisodeAndZeroCostDoNotConsumeUnmeasuredQuota() {
        var budget = new SwarmPathQueryBudget();
        assertTrue(budget.tryReserve(A, 0, 0, 8));
        assertTrue(budget.tryReserve(A, 0, 6, 8));
        assertFalse(budget.tryReserve(B, 0, 4, 8));
        assertEquals(6, budget.snapshot(0, 8).reservedTokens());
        assertEquals(1, budget.snapshot(0, 8).reservationsGranted());
    }

    @Test
    void configChangesCannotResurrectAlreadyUsedTokens() {
        var budget = new SwarmPathQueryBudget();
        assertTrue(budget.tryReserve(A, 4, 6, 8));
        assertEquals(6, budget.snapshot(4, 8).reservedTokens());
        assertEquals(6, budget.snapshot(4, 6).reservedTokens());
        assertFalse(budget.tryReserve(B, 4, 4, 6));
        assertEquals(0, budget.snapshot(5, 6).reservedTokens());
        assertTrue(budget.tryReserve(B, 5, 4, 6));
    }

    @Test
    void oldWaitingEpisodeCannotBeStarvedByLargeNewRequest() {
        var budget = new SwarmPathQueryBudget();
        assertTrue(budget.tryReserve(A, 0, 6, 8));
        assertFalse(budget.tryReserve(B, 0, 6, 8));
        assertFalse(budget.tryReserve(C, 1, 4, 8));
        assertTrue(budget.tryReserve(B, 1, 6, 8));
        assertTrue(budget.tryReserve(C, 2, 4, 8));
    }

    @Test
    void absentHeadDoesNotIdleTheEntireTick() {
        var budget = new SwarmPathQueryBudget();
        assertTrue(budget.tryReserve(A, 0, 6, 8));
        assertFalse(budget.tryReserve(B, 0, 6, 8));
        // B does not request on tick 1, but the unprotected remainder is usable.
        assertTrue(budget.tryReserve(C, 1, 2, 8));
        assertEquals(2, budget.snapshot(1, 8).reservedTokens());
        assertEquals(1, budget.snapshot(1, 8).waiters());
    }

    @Test
    void invalidRequestsAreRejectedRatherThanCorruptingState() {
        var budget = new SwarmPathQueryBudget();
        assertThrows(IllegalArgumentException.class,
                () -> budget.tryReserve(null, 0, 4, 8));
        assertThrows(IllegalArgumentException.class,
                () -> budget.tryReserve(A, 0, -1, 8));
        assertThrows(IllegalArgumentException.class,
                () -> budget.tryReserve(A, 0, 4, -1));
    }
}
