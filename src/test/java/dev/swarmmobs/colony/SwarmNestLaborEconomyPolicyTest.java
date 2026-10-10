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
}
