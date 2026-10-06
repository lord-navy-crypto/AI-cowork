package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmBridgeSpanPolicyTest {

    @Test
    void acceptsBoundedSpanOnlyWhenLandingAndMaterialsExist() {
        var accepted = SwarmBridgeSpanPolicy.evaluate(3, true, 3, 4);
        assertTrue(accepted.allowed());

        assertFalse(SwarmBridgeSpanPolicy.evaluate(3, false, 3, 4).allowed());
        assertFalse(SwarmBridgeSpanPolicy.evaluate(3, true, 2, 4).allowed());
        assertFalse(SwarmBridgeSpanPolicy.evaluate(5, true, 5, 4).allowed());
    }

    @Test
    void rejectsZeroLengthAndNormalizesBadInputs() {
        var evaluation = SwarmBridgeSpanPolicy.evaluate(-3, true, -5, 0);

        assertEquals(0, evaluation.gapLength());
        assertEquals(0, evaluation.availableMaterials());
        assertEquals(1, evaluation.maxSpan());
        assertFalse(evaluation.allowed());
    }
}
