package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmNavigationRecoveryPolicyTest {

    @Test
    void recoveryProducesLateralWaypointInsteadOfRetryingBlockedDestination() {
        var recovery = SwarmNavigationRecoveryPolicy.recoveryWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(10.0, 0.0),
                2,
                2.0
        );

        assertTrue(recovery.active());
        assertEquals(2.0, recovery.waypoint().x(), 1.0e-9);
        assertEquals(2.0, recovery.waypoint().z(), 1.0e-9);
    }

    @Test
    void neighboringEntityIdsChooseOppositeSides() {
        var even = SwarmNavigationRecoveryPolicy.recoveryWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(10.0, 0.0),
                2,
                2.0
        );
        var odd = SwarmNavigationRecoveryPolicy.recoveryWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(10.0, 0.0),
                3,
                2.0
        );

        assertEquals(even.waypoint().x(), odd.waypoint().x(), 1.0e-9);
        assertEquals(-even.waypoint().z(), odd.waypoint().z(), 1.0e-9);
    }

    @Test
    void zeroDistanceOrDisabledRecoveryFallsBackToDestination() {
        var samePoint = SwarmNavigationRecoveryPolicy.recoveryWaypoint(
                new Vec2(3.0, 4.0),
                new Vec2(3.0, 4.0),
                2,
                2.0
        );
        assertFalse(samePoint.active());
        assertEquals(new Vec2(3.0, 4.0), samePoint.waypoint());

        var disabled = SwarmNavigationRecoveryPolicy.recoveryWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(4.0, 0.0),
                2,
                0.0
        );
        assertFalse(disabled.active());
        assertEquals(new Vec2(4.0, 0.0), disabled.waypoint());
    }
}
