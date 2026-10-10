package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestArchitecturePolicyTest {
    @Test
    void capacityGrowsOnlyWithRealChamberLevels() {
        assertEquals(4, SwarmNestArchitecturePolicy.effectiveCapacity(0, 12));
        assertEquals(8, SwarmNestArchitecturePolicy.effectiveCapacity(1, 12));
        assertEquals(12, SwarmNestArchitecturePolicy.effectiveCapacity(2, 12));
        assertEquals(12, SwarmNestArchitecturePolicy.effectiveCapacity(7, 12));
        assertEquals(3, SwarmNestArchitecturePolicy.effectiveCapacity(0, 3));
    }

    @Test
    void expansionRequiresNearCapacityAndBothMaterials() {
        assertTrue(SwarmNestArchitecturePolicy.canExtend(0, 12, 3, 8, 6));
        assertFalse(SwarmNestArchitecturePolicy.canExtend(0, 12, 2, 8, 6));
        assertFalse(SwarmNestArchitecturePolicy.canExtend(0, 12, 3, 7, 6));
        assertFalse(SwarmNestArchitecturePolicy.canExtend(0, 12, 3, 8, 5));
        assertFalse(SwarmNestArchitecturePolicy.canExtend(0, 4, 4, 8, 6));
        assertFalse(SwarmNestArchitecturePolicy.canExtend(7, 32, 32, 100, 100));
    }

    @Test
    void resourcesNeverTransferFromOtherCategoriesToCoverBuildingCosts() {
        assertFalse(SwarmNestArchitecturePolicy.canExtend(0, 12, 4, 0, 128));
        assertFalse(SwarmNestArchitecturePolicy.canExtend(0, 12, 4, 128, 0));
    }
}
