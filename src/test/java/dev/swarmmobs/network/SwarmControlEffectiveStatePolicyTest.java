package dev.swarmmobs.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmControlEffectiveStatePolicyTest {

    @Test
    void dynamicAssignmentsAreHiddenWhenMasterOrLaborIsOff() {
        assertTrue(SwarmControlEffectiveStatePolicy.exposeDynamicAssignments(true, true));
        assertFalse(SwarmControlEffectiveStatePolicy.exposeDynamicAssignments(false, true));
        assertFalse(SwarmControlEffectiveStatePolicy.exposeDynamicAssignments(true, false));
    }

    @Test
    void activeAiRequiresEveryRuntimeGate() {
        assertTrue(SwarmControlEffectiveStatePolicy.exposeActiveAi(
                true, true, true, true));

        assertFalse(SwarmControlEffectiveStatePolicy.exposeActiveAi(
                false, true, true, true));
        assertFalse(SwarmControlEffectiveStatePolicy.exposeActiveAi(
                true, false, true, true));
        assertFalse(SwarmControlEffectiveStatePolicy.exposeActiveAi(
                true, true, false, true));
        assertFalse(SwarmControlEffectiveStatePolicy.exposeActiveAi(
                true, true, true, false));
    }
}
