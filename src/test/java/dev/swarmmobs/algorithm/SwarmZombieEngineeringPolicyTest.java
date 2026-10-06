package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmZombieEngineeringPolicyTest {

    @Test
    void rejectsUnbreakableTooHardAndBlockEntityTargets() {
        assertFalse(SwarmZombieEngineeringPolicy.canAttemptBreak(-1.0, 2.0, false));
        assertFalse(SwarmZombieEngineeringPolicy.canAttemptBreak(2.5, 2.0, false));
        assertFalse(SwarmZombieEngineeringPolicy.canAttemptBreak(1.0, 2.0, true));
        assertTrue(SwarmZombieEngineeringPolicy.canAttemptBreak(1.5, 2.0, false));
    }

    @Test
    void bareHandTimingIsSlowerWhenCorrectToolWouldBeRequired() {
        assertEquals(15, SwarmZombieEngineeringPolicy.handBreakTicks(0.5, false));
        assertEquals(150, SwarmZombieEngineeringPolicy.handBreakTicks(1.5, true));
        assertTrue(
                SwarmZombieEngineeringPolicy.handBreakTicks(1.5, true)
                        > SwarmZombieEngineeringPolicy.handBreakTicks(1.5, false)
        );
    }

    @Test
    void onlyHandHarvestableSolidBlockItemsBecomeBuildingMaterial() {
        assertTrue(SwarmZombieEngineeringPolicy.canSalvageAsBuildingMaterial(false, true, true));
        assertFalse(SwarmZombieEngineeringPolicy.canSalvageAsBuildingMaterial(true, true, true));
        assertFalse(SwarmZombieEngineeringPolicy.canSalvageAsBuildingMaterial(false, false, true));
        assertFalse(SwarmZombieEngineeringPolicy.canSalvageAsBuildingMaterial(false, true, false));
    }
}
