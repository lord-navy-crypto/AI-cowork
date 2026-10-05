package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmBehaviorMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmMovementPolicyTest {

    @Test
    void engageRetainsConfidenceBasedSlowdown() {
        assertEquals(
                0.55,
                SwarmMovementPolicy.speedFactor(
                        SwarmBehaviorMode.ENGAGE,
                        0.0,
                        0.55,
                        1.0
                ),
                1.0e-9
        );

        assertEquals(
                0.775,
                SwarmMovementPolicy.speedFactor(
                        SwarmBehaviorMode.ENGAGE,
                        0.5,
                        0.55,
                        1.0
                ),
                1.0e-9
        );

        assertEquals(
                1.0,
                SwarmMovementPolicy.speedFactor(
                        SwarmBehaviorMode.ENGAGE,
                        1.0,
                        0.55,
                        1.0
                ),
                1.0e-9
        );
    }

    @Test
    void searchUsesDedicatedCoverageSpeedEvenAtLowConfidence() {
        assertEquals(
                1.0,
                SwarmMovementPolicy.speedFactor(
                        SwarmBehaviorMode.SEARCH,
                        0.05,
                        0.55,
                        1.0
                ),
                1.0e-9
        );

        assertEquals(
                0.85,
                SwarmMovementPolicy.speedFactor(
                        SwarmBehaviorMode.SEARCH,
                        0.9,
                        0.55,
                        0.85
                ),
                1.0e-9
        );
    }

    @Test
    void engageClampsOutOfRangeInputs() {
        assertEquals(
                0.55,
                SwarmMovementPolicy.speedFactor(
                        SwarmBehaviorMode.ENGAGE,
                        -1.0,
                        0.55,
                        1.0
                ),
                1.0e-9
        );

        assertEquals(
                1.0,
                SwarmMovementPolicy.speedFactor(
                        SwarmBehaviorMode.ENGAGE,
                        2.0,
                        0.55,
                        1.0
                ),
                1.0e-9
        );
    }
}
