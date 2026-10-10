package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmPathProbePolicyTest {
    @Test
    void noEvidenceMeansNoPathQuotaRegardlessOfTerrain() {
        assertEquals(0, SwarmPathProbePolicy.requiredQueries(
                new boolean[]{false, false, false, false}, false));
    }

    @Test
    void reserveOnlyUnblockedObstacleCandidates() {
        assertEquals(2, SwarmPathProbePolicy.requiredQueries(
                new boolean[]{true, false, true, false}, true));
    }

    @Test
    void fullyBlockedWallsDoNotConsumeExpensivePathBudget() {
        assertEquals(0, SwarmPathProbePolicy.requiredQueries(
                new boolean[]{true, true, true, true}, true));
    }

    @Test
    void recoveryBatchReservesAtMostSixAndOnlyForOpenTerrain() {
        assertEquals(4, SwarmPathProbePolicy.requiredQueries(
                new boolean[]{true, false, false, true, false, false}, true));
    }

    @Test
    void nullAndEmptyBatchesUseNoQuota() {
        assertEquals(0, SwarmPathProbePolicy.requiredQueries(null, true));
        assertEquals(0, SwarmPathProbePolicy.requiredQueries(new boolean[]{}, true));
    }

    @Test
    void exactRequiredQueriesFitQuotaWhenOldWorstCaseWouldDefer() {
        int request = SwarmPathProbePolicy.requiredQueries(
                new boolean[]{true, true, false, false}, true);
        var budget = new SwarmPathQueryBudget();
        assertEquals(2, request);
        // Three budget tokens remain. The prior four-slot reservation would fail,
        // although precisely two candidates can actually call createPath.
        assertEquals(true, budget.tryReserve(new java.util.UUID(0L, 2L), 20, request, 3));
        assertEquals(2, budget.snapshot(20, 3).reservedTokens());
    }
}
