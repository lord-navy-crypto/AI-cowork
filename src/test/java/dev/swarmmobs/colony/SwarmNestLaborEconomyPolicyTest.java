package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestLaborEconomyPolicyTest {
    private static final SwarmNestColonyPolicy.Kind SOIL = SwarmNestColonyPolicy.Kind.SOIL;
    private static final SwarmNestColonyPolicy.Kind TIMBER = SwarmNestColonyPolicy.Kind.TIMBER;
    private static final SwarmNestColonyPolicy.Kind FOOD = SwarmNestColonyPolicy.Kind.NUTRIENT;

    @Test void lowPopulationStartsWithOneBuildBillAndTwoBirthBills() {
        var targets=SwarmNestLaborEconomyPolicy.targets(0,4,0,12);
        assertEquals(8,targets.soil());
        assertEquals(6,targets.timber());
        assertEquals(24,targets.food());
        assertTrue(SwarmNestLaborEconomyPolicy.needs(FOOD,0,0,0,0,targets));
        assertTrue(SwarmNestLaborEconomyPolicy.needs(SOIL,0,0,0,0,targets));
        assertTrue(SwarmNestLaborEconomyPolicy.needs(TIMBER,0,0,0,0,targets));
    }

    @Test void nearCapacityPreStocksRealConstructionMaterials() {
        var below=SwarmNestLaborEconomyPolicy.targets(0,8,1,16);
        var crowded=SwarmNestLaborEconomyPolicy.targets(7,8,1,16);
        assertEquals(8,below.soil());
        assertEquals(16,crowded.soil());
        assertEquals(12,crowded.timber());
        assertTrue(crowded.chamberPressure());
        assertTrue(SwarmNestLaborEconomyPolicy.needs(
                SOIL,8,6,0,14,crowded));
        assertFalse(SwarmNestLaborEconomyPolicy.needs(
                SOIL,16,12,0,28,crowded));
        assertTrue(SwarmNestLaborEconomyPolicy.deficit(
                SOIL,8,0,0,crowded)>0);
    }

    @Test void hardCapStopsEmptyWorkAndPreservesPhysicalResourceConservation() {
        var full=SwarmNestLaborEconomyPolicy.targets(12,12,2,12);
        assertFalse(full.canGrow());
        assertEquals(0,full.soil());
        assertEquals(0,full.timber());
        assertEquals(0,full.food());
        for(var kind: new SwarmNestColonyPolicy.Kind[]{SOIL,TIMBER,FOOD}) {
            assertFalse(SwarmNestLaborEconomyPolicy.needs(kind,0,0,0,0,full));
            assertEquals(0,SwarmNestLaborEconomyPolicy.deficit(
                    kind,0,0,0,full),1e-10);
        }
        var waiting=SwarmNestLaborEconomyPolicy.targets(8,8,1,12);
        assertEquals(12,waiting.food());
        assertEquals(16,waiting.soil());
        assertEquals(12,waiting.timber());
    }

    @Test void storageLimitsAndWholeItemValuesRemainHardConstraints() {
        var targets=SwarmNestLaborEconomyPolicy.targets(0,4,0,12);
        assertFalse(SwarmNestLaborEconomyPolicy.needs(
                FOOD,0,0,0,128,targets));
        assertFalse(SwarmNestLaborEconomyPolicy.needs(
                FOOD,0,0,0,127,targets));
        assertTrue(SwarmNestLaborEconomyPolicy.needs(
                SOIL,0,0,0,127,targets));
        assertFalse(SwarmNestLaborEconomyPolicy.needs(
                SwarmNestColonyPolicy.Kind.NONE,0,0,0,0,targets));
    }
    @Test void demandLimitedIntakeLeavesSurplusPhysicalItemsInWorld() {
        var target = SwarmNestLaborEconomyPolicy.targets(0,4,0,12);
        // A 64-stack must not monopolize the finite 128-point store.
        assertEquals(2,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                TIMBER,64,0,0,0,0,target));
        assertEquals(8,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                SOIL,64,6,0,6,0,target));
        assertEquals(6,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                FOOD,64,14,8,6,0,target));
        assertEquals(0,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                TIMBER,64,6,0,6,0,target));
        assertEquals(0,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                SwarmNestColonyPolicy.Kind.NONE,64,0,0,0,0,target));
        // Never split an item into fractional resource points.
        assertEquals(1,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                TIMBER,64,5,0,5,0,target));
    }

    @Test void finiteGlobalStoreAlwaysWinsOverCategoryDemand() {
        var target = SwarmNestLaborEconomyPolicy.targets(0,4,0,12);
        assertEquals(0,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                FOOD,64,126,0,0,0,target));
        assertEquals(1,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                SOIL,64,127,0,0,0,target));
        assertEquals(0,SwarmNestLaborEconomyPolicy.demandedItemLimit(
                FOOD,64,128,0,0,0,target));
    }

}
