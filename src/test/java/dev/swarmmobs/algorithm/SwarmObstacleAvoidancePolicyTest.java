package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmObstacleAvoidancePolicyTest {

    @Test
    void unobstructedPathKeepsOriginalDestination() {
        var result = SwarmObstacleAvoidancePolicy.chooseWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(8.0, 0.0),
                2,
                false,
                false,
                false,
                1.5,
                1.5
        );

        assertFalse(result.active());
        assertEquals(new Vec2(8.0, 0.0), result.waypoint());
    }

    @Test
    void choosesOnlyOpenSideWhenFrontIsBlocked() {
        var leftOpen = SwarmObstacleAvoidancePolicy.chooseWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(8.0, 0.0),
                3,
                true,
                false,
                true,
                1.5,
                1.5
        );
        assertTrue(leftOpen.active());
        assertEquals(1, leftOpen.side());
        assertTrue(leftOpen.waypoint().z() > 0.0);

        var rightOpen = SwarmObstacleAvoidancePolicy.chooseWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(8.0, 0.0),
                2,
                true,
                true,
                false,
                1.5,
                1.5
        );
        assertTrue(rightOpen.active());
        assertEquals(-1, rightOpen.side());
        assertTrue(rightOpen.waypoint().z() < 0.0);
    }

    @Test
    void splitsAgentsAcrossBothOpenSidesDeterministically() {
        var even = SwarmObstacleAvoidancePolicy.chooseWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(8.0, 0.0),
                2,
                true,
                false,
                false,
                1.5,
                1.5
        );
        var odd = SwarmObstacleAvoidancePolicy.chooseWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(8.0, 0.0),
                3,
                true,
                false,
                false,
                1.5,
                1.5
        );

        assertEquals(1, even.side());
        assertEquals(-1, odd.side());
        assertEquals(even.waypoint().x(), odd.waypoint().x(), 1.0e-9);
        assertEquals(-even.waypoint().z(), odd.waypoint().z(), 1.0e-9);
    }

    @Test
    void givesUpWhenBothSidesAreBlocked() {
        var result = SwarmObstacleAvoidancePolicy.chooseWaypoint(
                new Vec2(0.0, 0.0),
                new Vec2(8.0, 0.0),
                2,
                true,
                true,
                true,
                1.5,
                1.5
        );

        assertFalse(result.active());
        assertEquals(new Vec2(8.0, 0.0), result.waypoint());
    }
}
