package dev.swarmmobs.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwarmLocalCompositionTest {

    @Test
    void countsSelfAndNeighborsByCapability() {
        SwarmLocalComposition composition = SwarmLocalComposition.fromArchetypes(
                SwarmAgentArchetype.ASSAULT,
                List.of(
                        SwarmAgentArchetype.ASSAULT,
                        SwarmAgentArchetype.RANGED_SUPPORT,
                        SwarmAgentArchetype.FLANKER,
                        SwarmAgentArchetype.FLANKER
                )
        );

        assertEquals(2, composition.assaultCount());
        assertEquals(1, composition.rangedSupportCount());
        assertEquals(2, composition.flankerCount());
        assertEquals(0, composition.breacherCount());
        assertTrue(composition.dedicatedFlankCoverage());
    }

    @Test
    void tracksBreacherCapabilitySeparatelyFromAssault() {
        SwarmLocalComposition composition = SwarmLocalComposition.fromArchetypes(
                SwarmAgentArchetype.RANGED_SUPPORT,
                List.of(
                        SwarmAgentArchetype.BREACHER,
                        SwarmAgentArchetype.ASSAULT
                )
        );

        assertEquals(1, composition.breacherCount());
        assertEquals(1, composition.assaultCount());
        assertEquals(1, composition.rangedSupportCount());
        assertTrue(composition.hasBreacher());
    }

    @Test
    void oneFlankerDoesNotCountAsFullFlankCoverage() {
        SwarmLocalComposition composition = SwarmLocalComposition.fromArchetypes(
                SwarmAgentArchetype.ASSAULT,
                List.of(SwarmAgentArchetype.FLANKER)
        );

        assertFalse(composition.dedicatedFlankCoverage());
    }
}
