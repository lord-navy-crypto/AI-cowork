package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwarmSearchPlannerTest {

    @Test
    void searchRadiusExpandsAsConfidenceFalls() {
        var fresh = plan(SwarmAgentArchetype.FLANKER, 0, 0.4, 60L);
        var stale = plan(SwarmAgentArchetype.FLANKER, 0, 0.1, 90L);

        assertTrue(stale.searchRadius() > fresh.searchRadius());
    }

    @Test
    void archetypesOccupyDifferentSearchBands() {
        var assault = plan(SwarmAgentArchetype.ASSAULT, 0, 0.2, 80L);
        var flanker = plan(SwarmAgentArchetype.FLANKER, 0, 0.2, 80L);
        var ranged = plan(SwarmAgentArchetype.RANGED_SUPPORT, 0, 0.2, 80L);

        assertTrue(assault.searchRadius() < flanker.searchRadius());
        assertTrue(flanker.searchRadius() < ranged.searchRadius());
    }

    @Test
    void searchSectorRotatesAsInformationAges() {
        var first = plan(SwarmAgentArchetype.FLANKER, 0, 0.3, 40L);
        var later = plan(SwarmAgentArchetype.FLANKER, 0, 0.3, 80L);

        assertNotEquals(first.angleRadians(), later.angleRadians());
        assertNotEquals(first.destination(), later.destination());
    }

    @Test
    void twoSameCapabilitySlotsCoverDifferentSectors() {
        var left = planWithSectors(SwarmAgentArchetype.FLANKER, 0, 0.3, 60L, 2);
        var right = planWithSectors(SwarmAgentArchetype.FLANKER, 1, 0.3, 60L, 2);

        assertTrue(left.destination().subtract(right.destination()).length() > 2.0);
    }

    private static SwarmSearchPlanner.SearchPlan plan(
            SwarmAgentArchetype archetype,
            int slot,
            double confidence,
            long age
    ) {
        return planWithSectors(archetype, slot, confidence, age, 4);
    }

    private static SwarmSearchPlanner.SearchPlan planWithSectors(
            SwarmAgentArchetype archetype,
            int slot,
            double confidence,
            long age,
            int sectors
    ) {
        return SwarmSearchPlanner.planWithMotion(
                archetype,
                slot,
                new Vec2(0.0, 0.0),
                new Vec2(0.0, 0.0),
                new Vec2(10.0, 10.0),
                confidence,
                age,
                List.of(),
                List.of(),
                sectors,
                2.0,
                10.0,
                20,
                2.4,
                0.0,
                0.0,
                0.0,
                3.0
        );
    }
}
