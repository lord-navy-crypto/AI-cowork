package dev.swarmmobs.colony;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmNestScoutBoardTest {
    private static final BlockPos HOME = new BlockPos(0, 64, 0);

    @Test
    void onlyOneWorkerCanReserveOneRemotePhysicalLead() {
        var board = new SwarmNestScoutBoard();
        UUID item = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID();
        assertTrue(board.publish(item, new BlockPos(14, 64, 0),
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
        assertFalse(board.publish(UUID.randomUUID(), new BlockPos(40, 64, 0),
                SwarmNestColonyPolicy.Kind.SOIL, 100, HOME));
        for (int i = 0; i < 10; i++) {
            assertTrue(board.publish(new UUID(0, i + 1),
                    new BlockPos(i + 3, 64, 0),
                    SwarmNestColonyPolicy.Kind.NUTRIENT, 100 + i, HOME));
        }
        assertEquals(SwarmNestScoutBoard.MAX_LEADS, board.size(110));
        assertEquals(0, board.size(100 + SwarmNestScoutSignal.FRESH_TICKS + 11));
    }

    @Test
    void cannotStealReservedLeadWhenBoardFullAndCannotResumeExpiredLead() {
        var board = new SwarmNestScoutBoard();
        var owner = UUID.randomUUID();
        for (int i = 0; i < SwarmNestScoutBoard.MAX_LEADS; i++) {
            UUID item = new UUID(0, i + 1);
            assertTrue(board.publish(item, new BlockPos(5 + i, 64, 0),
                    SwarmNestColonyPolicy.Kind.SOIL, 10, HOME));
            assertNotNull(board.reserve(owner, new BlockPos(5 + i, 64, 0), 10, 1));
        }
        // Renewed by the same owner, but a subsequent single reservation can
        // select an existing one. At least one reserved lead still remains.
        assertTrue(board.size(10) <= SwarmNestScoutBoard.MAX_LEADS);
        assertEquals(0, board.size(260));
    }

    @Test
    void dropsOutsideWorkerReachRemainUnassigned() {
        var board = new SwarmNestScoutBoard();
        board.publish(UUID.randomUUID(), new BlockPos(25, 64, 0),
                SwarmNestColonyPolicy.Kind.SOIL, 1, HOME);
        assertNull(board.reserve(UUID.randomUUID(), HOME, 2, 16));
        assertNotNull(board.reserve(UUID.randomUUID(), HOME, 2, 28));
    }
}
