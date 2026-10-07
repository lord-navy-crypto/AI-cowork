package dev.swarmmobs.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmAiControlPolicyTest {

    @Test
    void activeStrategyRequiresBothMasterAndActiveSwitch() {
        assertTrue(SwarmAiControlPolicy.mayApplyActiveStrategy(true, true));
        assertFalse(SwarmAiControlPolicy.mayApplyActiveStrategy(false, true));
        assertFalse(SwarmAiControlPolicy.mayApplyActiveStrategy(true, false));
        assertFalse(SwarmAiControlPolicy.mayApplyActiveStrategy(false, false));
    }
}
