package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmNestScoutBoardTest {
    private static final SwarmNestScoutBoard.Position HOME = new SwarmNestScoutBoard.Position(0, 64, 0);

    @Test
    void onlyOneWorkerCanReserveOneRemotePhysicalLead() {
        var board = new SwarmNestScoutBoard();
        UUID item = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID();
        assertTrue(board.publish(item, new SwarmNestScoutBoard.Position(14, 64, 0),
                SwarmNestColonyPolicy.Kind.TIMBER, 100, HOME));
        var lead = board.reserve(alice, HOME, 105, 25);
        assertNotNull(lead);
        assertEquals(item, lead.itemId());
        assertNull(board.reserve(bob, HOME, 105, 25));
        assertTrue(board.renew(item, alice, 120));
        assertFalse(board.renew(item, bob, 120));
        board.release(item, alice);
        assertNotNull(board.reserve(bob, HOME, 121, 25));
        assertFalse(board.renew(item, alice, 122));
    }

    @Test
    void limitedLifetimeLoadedRangeAndQueueSize() {
        var board = new SwarmNestScoutBoard();
        assertFalse(board.publish(UUID.randomUUID(), new SwarmNestScoutBoard.Position(40, 64, 0),
                SwarmNestColonyPolicy.Kind.SOIL, 100, HOME));
        for (int i = 0; i < 10; i++) {
            assertTrue(board.publish(new UUID(0, i + 1),
                    new SwarmNestScoutBoard.Position(i + 3, 64, 0),
                    SwarmNestColonyPolicy.Kind.NUTRIENT, 100 + i, HOME));
        }
        assertEquals(SwarmNestScoutBoard.MAX_LEADS, board.size(110));
        assertEquals(0, board.size(100 + SwarmNestScoutSignal.FRESH_TICKS + 11));
    }

    @Test
    void cannotStealReservedLeadWhenBoardFullAndCannotResumeExpiredLead() {
        var board = new SwarmNestScoutBoard();
        for (int i = 0; i < SwarmNestScoutBoard.MAX_LEADS; i++) {
            UUID item = new UUID(0, i + 1);
            var location = new SwarmNestScoutBoard.Position(5 + i, 64, 0);
            assertTrue(board.publish(item, location,
                    SwarmNestColonyPolicy.Kind.SOIL, 10, HOME));
            var lead = board.reserve(new UUID(1, i + 1), location, 10, 1);
            assertNotNull(lead);
            assertEquals(item, lead.itemId());
        }
        assertFalse(board.publish(new UUID(0, 999),
                new SwarmNestScoutBoard.Position(20, 64, 0),
                SwarmNestColonyPolicy.Kind.SOIL, 10, HOME));
        assertEquals(SwarmNestScoutBoard.MAX_LEADS, board.size(10));
        assertEquals(0, board.size(260));
    }

    @Test
    void shortageScoreAndCapacityGateDetermineWhichSpiderLeadGetsAssigned() {
        var board = new SwarmNestScoutBoard();
        UUID soil = UUID.randomUUID(), food = UUID.randomUUID();
        assertTrue(board.publish(soil, new SwarmNestScoutBoard.Position(5, 64, 0),
                SwarmNestColonyPolicy.Kind.SOIL, 10, HOME));
        assertTrue(board.publish(food, new SwarmNestScoutBoard.Position(7, 64, 0),
                SwarmNestColonyPolicy.Kind.NUTRIENT, 10, HOME));
        var chosen = board.reserve(UUID.randomUUID(), HOME, 11, 20,
                lead -> SwarmNestColonyPolicy.acceptAmount(0, 1, lead.kind()) > 0,
                lead -> SwarmNestHaulPolicy.pickupScore(
                        lead.kind(), HOME.distanceSquared(lead.position()),
                        8, 6, 0, 0, 0, 12));
        assertNotNull(chosen);
        assertEquals(food, chosen.itemId(), "Food shortage should beat a modest extra trip");
        var second = board.reserve(UUID.randomUUID(), HOME, 11, 20,
                lead -> lead.kind() == SwarmNestColonyPolicy.Kind.SOIL,
                lead -> HOME.distanceSquared(lead.position()));
        assertNotNull(second);
        assertEquals(soil, second.itemId());
    }

    @Test
    void physicalPickupCanInvalidateRemoteLeadWithoutLeavingAnyClaim() {
        var board = new SwarmNestScoutBoard();
        UUID item = UUID.randomUUID(), first = UUID.randomUUID();
        assertTrue(board.publish(item, new SwarmNestScoutBoard.Position(12, 64, 0),
                SwarmNestColonyPolicy.Kind.TIMBER, 30, HOME));
        assertNotNull(board.reserve(first, HOME, 31, 20));
        board.discard(item);
        assertFalse(board.renew(item, first, 32));
        assertNull(board.reserve(UUID.randomUUID(), HOME, 32, 20));
        assertEquals(0, board.size(32));
    }

    @Test
    void dropsOutsideWorkerReachRemainUnassigned() {
        var board = new SwarmNestScoutBoard();
        board.publish(UUID.randomUUID(), new SwarmNestScoutBoard.Position(25, 64, 0),
                SwarmNestColonyPolicy.Kind.SOIL, 1, HOME);
        assertNull(board.reserve(UUID.randomUUID(), HOME, 2, 16));
        assertNotNull(board.reserve(UUID.randomUUID(), HOME, 2, 28));
    }
}
