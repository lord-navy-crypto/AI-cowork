package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmObstacleHoldPolicyTest {

    @Test
    void keepsDetourBeforeExpiryWhileWaypointIsNotReached() {
        assertTrue(SwarmObstacleHoldPolicy.shouldKeepDetour(
                100L,
                112L,
                1.2,
                0.6
        ));
    }

    @Test
    void releasesDetourAtExpiry() {
        assertFalse(SwarmObstacleHoldPolicy.shouldKeepDetour(
                112L,
                112L,
                1.2,
                0.6
        ));
    }

    @Test
    void releasesDetourEarlyWhenWaypointIsReached() {
        assertFalse(SwarmObstacleHoldPolicy.shouldKeepDetour(
                105L,
                112L,
                0.4,
                0.6
        ));
    }

    @Test
    void holdDeadlineClampsNegativeDurations() {
        assertEquals(50L, SwarmObstacleHoldPolicy.holdUntil(50L, -5));
        assertEquals(62L, SwarmObstacleHoldPolicy.holdUntil(50L, 12));
    }
}
