package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmColonyFoundationTest {
    @Test
    void idleJobsFollowSpeciesCompetenceRatherThanGlobalQueenOrders() {
        assertEquals(SwarmColonyCastePolicy.Caste.WORKER,
                SwarmColonyCastePolicy.idleCaste(
                        SwarmAgentArchetype.ASSAULT, true, false));
        assertEquals(SwarmColonyCastePolicy.Caste.GUARD,
                SwarmColonyCastePolicy.idleCaste(
                        SwarmAgentArchetype.RANGED_SUPPORT, true, false));
        assertEquals(SwarmColonyCastePolicy.Caste.SCOUT,
                SwarmColonyCastePolicy.idleCaste(
                        SwarmAgentArchetype.FLANKER, true, false));
        assertEquals(SwarmColonyCastePolicy.Caste.RESERVE,
                SwarmColonyCastePolicy.idleCaste(
                        SwarmAgentArchetype.BREACHER, true, false));
        assertEquals(SwarmColonyCastePolicy.Caste.RESERVE,
                SwarmColonyCastePolicy.idleCaste(
                        SwarmAgentArchetype.ASSAULT, false, false));
    }

    @Test
    void activeCombatOverridesIdleCasteBias() {
        for (var archetype : SwarmAgentArchetype.values()) {
            assertEquals(SwarmColonyCastePolicy.Caste.RESERVE,
                    SwarmColonyCastePolicy.idleCaste(archetype, true, true));
        }
    }

    @Test
    void noTerrainModificationWithoutExplicitEnablementAndMobGriefing() {
        assertFalse(SwarmNestSitePolicy.eligible(
                false, true, false, false, false, 8, 3, true, false));
        assertFalse(SwarmNestSitePolicy.eligible(
                true, false, false, false, false, 8, 3, true, false));
        assertFalse(SwarmNestSitePolicy.eligible(
                true, true, true, false, false, 8, 3, true, false));
        assertFalse(SwarmNestSitePolicy.eligible(
                true, true, false, true, false, 8, 3, true, false));
        assertFalse(SwarmNestSitePolicy.eligible(
                true, true, false, false, true, 8, 3, true, false));
    }

    @Test
    void colonyFoundingRequiresPopulationCleanSoilAndNoOldNest() {
        assertFalse(SwarmNestSitePolicy.eligible(
                true, true, false, false, false, 2, 3, true, false));
        assertFalse(SwarmNestSitePolicy.eligible(
                true, true, false, false, false, 3, 3, false, false));
        assertFalse(SwarmNestSitePolicy.eligible(
                true, true, false, false, false, 3, 3, true, true));
        assertTrue(SwarmNestSitePolicy.eligible(
                true, true, false, false, false, 3, 3, true, false));
    }

    @Test
    void siteFoundationMustBeNaturalDryAndEmpty() {
        assertTrue(SwarmNestSitePolicy.naturalFoundation(true, true, true, false));
        assertFalse(SwarmNestSitePolicy.naturalFoundation(false, true, true, false));
        assertFalse(SwarmNestSitePolicy.naturalFoundation(true, false, true, false));
        assertFalse(SwarmNestSitePolicy.naturalFoundation(true, true, false, false));
        assertFalse(SwarmNestSitePolicy.naturalFoundation(true, true, true, true));
    }

    @Test
    void dimensionSurveyBudgetRejectsConsecutiveMassSpawnAttempts() {
        var budget = new SwarmNestSurveyBudget();
        assertTrue(budget.trySurvey(100L, 40));
        assertFalse(budget.trySurvey(100L, 40));
        assertFalse(budget.trySurvey(139L, 40));
        assertTrue(budget.trySurvey(140L, 40));
    }
}
