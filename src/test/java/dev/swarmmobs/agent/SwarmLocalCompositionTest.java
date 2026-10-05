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
        assertTrue(composition.dedicatedFlankCoverage());
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
