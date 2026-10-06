package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmRangedHandoffPolicyTest {

    @Test
    void yieldsInsideVisibleDirectRangedEnvelope() {
        assertTrue(SwarmRangedHandoffPolicy.shouldYieldToVanilla(
                true,
                true,
                true,
                10.0 * 10.0,
                15.0
        ));
    }

    @Test
    void keepsSwarmMovementOutsideRangedEnvelope() {
        assertFalse(SwarmRangedHandoffPolicy.shouldYieldToVanilla(
                true,
                true,
                true,
                18.0 * 18.0,
                15.0
        ));
    }

    @Test
    void doesNotYieldForIndirectOrOccludedTarget() {
        assertFalse(SwarmRangedHandoffPolicy.shouldYieldToVanilla(
                false,
                true,
                true,
                8.0 * 8.0,
                15.0
        ));

        assertFalse(SwarmRangedHandoffPolicy.shouldYieldToVanilla(
                true,
                true,
                false,
                8.0 * 8.0,
                15.0
        ));
    }

    @Test
    void doesNotYieldForDifferentTarget() {
        assertFalse(SwarmRangedHandoffPolicy.shouldYieldToVanilla(
                true,
                false,
                true,
                8.0 * 8.0,
                15.0
        ));
    }
}
