package dev.swarmmobs.ai;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmLocalComposition;
import dev.swarmmobs.agent.SwarmRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmAiRoleBiasPolicyTest {

    @Test
    void encircleMayBiasAssaultTowardAlternatingFlanks() {
        assertEquals(
                SwarmRole.FLANK_LEFT,
                SwarmAiRoleBiasPolicy.apply(
                        SwarmStrategyDecision.Mode.ENCIRCLE,
                        SwarmAgentArchetype.ASSAULT,
                        SwarmRole.CHASER,
                        0,
                        new SwarmLocalComposition(3, 1, 0, 1)
                )
        );
        assertEquals(
                SwarmRole.FLANK_RIGHT,
                SwarmAiRoleBiasPolicy.apply(
                        SwarmStrategyDecision.Mode.ENCIRCLE,
                        SwarmAgentArchetype.ASSAULT,
                        SwarmRole.CHASER,
                        1,
                        new SwarmLocalComposition(3, 1, 0, 1)
                )
        );
    }

    @Test
    void dedicatedSpiderFlanksRemainAuthoritative() {
        assertEquals(
                SwarmRole.CHASER,
                SwarmAiRoleBiasPolicy.apply(
                        SwarmStrategyDecision.Mode.ENCIRCLE,
                        SwarmAgentArchetype.ASSAULT,
                        SwarmRole.CHASER,
                        0,
                        new SwarmLocalComposition(3, 1, 2, 1)
                )
        );
    }

    @Test
    void specialistArchetypesAreNeverRewrittenByAiMode() {
        assertEquals(
                SwarmRole.RANGED_SUPPORT,
                SwarmAiRoleBiasPolicy.apply(
                        SwarmStrategyDecision.Mode.CONCENTRATE,
                        SwarmAgentArchetype.RANGED_SUPPORT,
                        SwarmRole.RANGED_SUPPORT,
                        0,
                        new SwarmLocalComposition(1, 1, 0, 1)
                )
        );
        assertEquals(
                SwarmRole.FLANK_LEFT,
                SwarmAiRoleBiasPolicy.apply(
                        SwarmStrategyDecision.Mode.CONCENTRATE,
                        SwarmAgentArchetype.FLANKER,
                        SwarmRole.FLANK_LEFT,
                        0,
                        new SwarmLocalComposition(1, 1, 1, 1)
                )
        );
        assertEquals(
                SwarmRole.CHASER,
                SwarmAiRoleBiasPolicy.apply(
                        SwarmStrategyDecision.Mode.ENCIRCLE,
                        SwarmAgentArchetype.BREACHER,
                        SwarmRole.CHASER,
                        0,
                        new SwarmLocalComposition(1, 1, 0, 1)
                )
        );
    }
}
