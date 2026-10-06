package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmLocalComposition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmSupportSpacingPolicyTest {

    @Test
    void rangedSupportWidensStandoffWhenBreacherIsLocal() {
        double multiplier = SwarmSupportSpacingPolicy.formationRadiusMultiplier(
                SwarmAgentArchetype.RANGED_SUPPORT,
                new SwarmLocalComposition(1, 1, 0, 1)
        );

        assertEquals(
                SwarmSupportSpacingPolicy.BREACHER_SUPPORT_STANDOFF_MULTIPLIER,
                multiplier,
                1.0e-9
        );
    }

    @Test
    void nonSupportAgentsDoNotReceiveBreacherSpacing() {
        assertEquals(
                1.0,
                SwarmSupportSpacingPolicy.formationRadiusMultiplier(
                        SwarmAgentArchetype.BREACHER,
                        new SwarmLocalComposition(0, 1, 0, 1)
                ),
                1.0e-9
        );
    }
}
