package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmCreeperHandoffPolicyTest {

    @Test
    void yieldsInsideVisibleDirectFuseEnvelope() {
        assertTrue(SwarmCreeperHandoffPolicy.shouldYieldToSwell(
                true, true, true,
                2.5 * 2.5,
                3.0,
                -1,
                false
        ));
    }

    @Test
    void keepsSwarmApproachOutsideFuseEnvelope() {
        assertFalse(SwarmCreeperHandoffPolicy.shouldYieldToSwell(
                true, true, true,
                5.0 * 5.0,
                3.0,
                -1,
                false
        ));
    }

    @Test
    void activeFuseAlwaysKeepsVanillaControl() {
        assertTrue(SwarmCreeperHandoffPolicy.shouldYieldToSwell(
                false, false, false,
                100.0,
                3.0,
                1,
                false
        ));

        assertTrue(SwarmCreeperHandoffPolicy.shouldYieldToSwell(
                false, false, false,
                100.0,
                3.0,
                -1,
                true
        ));
    }

    @Test
    void indirectOrOccludedTargetDoesNotStartFuseHandoff() {
        assertFalse(SwarmCreeperHandoffPolicy.shouldYieldToSwell(
                false, true, true,
                2.0,
                3.0,
                -1,
                false
        ));
        assertFalse(SwarmCreeperHandoffPolicy.shouldYieldToSwell(
                true, true, false,
                2.0,
                3.0,
                -1,
                false
        ));
    }
}
