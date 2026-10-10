package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SwarmColonyWorkBoardTest {
    @Test void sharedSiteCannotBeAssignedToTwoWorkersAtOnce() {
        var board = new SwarmColonyWorkBoard();
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        assertTrue(board.claim(124L, alice, 100));
        assertTrue(board.claim(124L, alice, 120));
        assertTrue(board.owned(124L, alice, 120));
        assertFalse(board.claim(124L, bob, 121));
        assertTrue(board.claimedByAnother(124L, bob, 121));
        board.release(124L, bob);
        assertTrue(board.owned(124L, alice, 122));
        board.release(124L, alice);
        assertTrue(board.claim(124L, bob, 123));
    }
    @Test void expiredOrCancelledJobsNeverHoldResourcesPermanently() {
        var board = new SwarmColonyWorkBoard();
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        assertTrue(board.claim(10, a, 100));
        assertFalse(board.claim(10, b, 160));
        assertTrue(board.claim(10, b, 161));
        assertFalse(board.owned(10, a, 161));
        assertEquals(1,board.size(161));
        assertEquals(0,board.size(222));
        assertTrue(board.claim(10, a, 223));
    }
    @Test void reservationMemoryRemainsBoundedAtLargePopulations() {
        var board = new SwarmColonyWorkBoard();
        UUID a = UUID.randomUUID();
        for (int i=0;i<SwarmColonyWorkBoard.MAX_SITES;i++) {
            assertTrue(board.claim(i,a,0));
        }
        assertFalse(board.claim(9999,a,1));
        assertEquals(SwarmColonyWorkBoard.MAX_SITES,board.size(1));
        assertEquals(0,board.size(61));
        assertTrue(board.claim(9999,a,62));
        assertFalse(board.claim(100,null,62));
    }
}
