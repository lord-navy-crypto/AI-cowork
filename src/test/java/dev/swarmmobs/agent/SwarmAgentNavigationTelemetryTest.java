package dev.swarmmobs.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmAgentNavigationTelemetryTest {

    @Test
    void navigationTelemetryTracksModeWaypointAndCounters() {
        SwarmAgentState state = new SwarmAgentState();

        state.updateNavigationTelemetry(
                SwarmNavigationMode.OBSTACLE_DETOUR,
                4.0,
                5.0,
                true
        );

        assertEquals(SwarmNavigationMode.OBSTACLE_DETOUR, state.navigationMode());
        assertTrue(state.hasNavigationWaypoint());
        assertEquals(4.0, state.navigationWaypointX(), 1.0e-9);
        assertEquals(5.0, state.navigationWaypointZ(), 1.0e-9);
        assertEquals(1L, state.obstacleDetourCount());
        assertEquals(0L, state.recoveryCount());

        state.updateNavigationTelemetry(
                SwarmNavigationMode.RECOVERY,
                6.0,
                7.0,
                true
        );

        assertEquals(1L, state.obstacleDetourCount());
        assertEquals(1L, state.recoveryCount());
    }

    @Test
    void repeatedRefreshDoesNotIncrementCounterWithoutTransition() {
        SwarmAgentState state = new SwarmAgentState();

        state.updateNavigationTelemetry(
                SwarmNavigationMode.OBSTACLE_DETOUR,
                1.0,
                2.0,
                true
        );
        state.updateNavigationTelemetry(
                SwarmNavigationMode.OBSTACLE_DETOUR,
                1.5,
                2.5,
                false
        );

        assertEquals(1L, state.obstacleDetourCount());
    }

    @Test
    void clearingTelemetryReturnsToPlanWithoutWaypoint() {
        SwarmAgentState state = new SwarmAgentState();
        state.updateNavigationTelemetry(
                SwarmNavigationMode.RECOVERY,
                2.0,
                3.0,
                true
        );

        state.clearNavigationTelemetry();

        assertEquals(SwarmNavigationMode.PLAN, state.navigationMode());
        assertFalse(state.hasNavigationWaypoint());
        assertEquals(1L, state.recoveryCount());
    }
    @Test
    void recoveryPlanningCountersTrackSuccessAndFailure() {
        SwarmAgentState state = new SwarmAgentState();

        state.recordRecoveryPlanning(true);
        state.recordRecoveryPlanning(false);
        state.recordRecoveryPlanning(false);

        assertEquals(3L, state.recoveryPlanningAttempts());
        assertEquals(2L, state.recoveryPlanningFailures());
    }
}
