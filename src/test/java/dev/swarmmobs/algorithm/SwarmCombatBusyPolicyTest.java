package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmCombatBusyPolicyTest {

    @Test
    void closeVisibleTargetIsCombatBusy() {
        assertTrue(SwarmCombatBusyPolicy.isBusy(true, true, 4.0, 3.25));
    }

    @Test
    void closeTargetBehindWallIsNotCombatBusy() {
        assertFalse(SwarmCombatBusyPolicy.isBusy(true, false, 4.0, 3.25));
    }

    @Test
    void visibleButDistantTargetIsNotCombatBusy() {
        assertFalse(SwarmCombatBusyPolicy.isBusy(true, true, 25.0, 3.25));
    }
}
