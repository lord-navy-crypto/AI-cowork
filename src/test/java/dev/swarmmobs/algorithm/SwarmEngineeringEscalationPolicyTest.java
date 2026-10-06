package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmPlannerContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmEngineeringEscalationPolicyTest {

    @Test
    void ordinaryDetourWithFeasibleEscapeKeepsNavigationPriority() {
        assertFalse(SwarmEngineeringEscalationPolicy.shouldEscalate(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                2,
                0,
                2
        ));
    }

    @Test
    void noFeasibleRouteEscalatesImmediately() {
        assertTrue(SwarmEngineeringEscalationPolicy.shouldEscalate(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                3,
                1,
                0
        ));
    }

    @Test
    void recoveryEscalatesEvenWhenOneFallbackCandidateStillExists() {
        assertTrue(SwarmEngineeringEscalationPolicy.shouldEscalate(
                SwarmPlannerContext.RECOVERY,
                3,
                2,
                1
        ));
    }

    @Test
    void recoveryWithoutObstacleEvidenceDoesNotInventEngineeringWork() {
        assertFalse(SwarmEngineeringEscalationPolicy.shouldEscalate(
                SwarmPlannerContext.RECOVERY,
                0,
                0,
                2
        ));
    }

    @Test
    void noneContextNeverEscalates() {
        assertFalse(SwarmEngineeringEscalationPolicy.shouldEscalate(
                SwarmPlannerContext.NONE,
                4,
                0,
                0
        ));
    }
}
