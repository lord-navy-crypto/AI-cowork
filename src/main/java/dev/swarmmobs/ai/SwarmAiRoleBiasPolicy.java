package dev.swarmmobs.ai;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmLocalComposition;
import dev.swarmmobs.agent.SwarmRole;

/**
 * Very narrow role influence for active AI strategy.
 *
 * Specialist archetypes are never rewritten. Only ASSAULT agents may receive
 * a high-level bias, and dedicated flanker coverage remains authoritative.
 */
public final class SwarmAiRoleBiasPolicy {

    public static SwarmRole apply(
            SwarmStrategyDecision.Mode mode,
            SwarmAgentArchetype archetype,
            SwarmRole deterministicRole,
            int stableSlot,
            SwarmLocalComposition composition
    ) {
        SwarmRole baseline = deterministicRole == null
                ? SwarmRole.CHASER
                : deterministicRole;

        if (archetype != SwarmAgentArchetype.ASSAULT || mode == null) {
            return baseline;
        }

        return switch (mode) {
            case ENCIRCLE -> {
                if (composition != null && composition.dedicatedFlankCoverage()) {
                    yield baseline;
                }
                yield Math.floorMod(stableSlot, 2) == 0
                        ? SwarmRole.FLANK_LEFT
                        : SwarmRole.FLANK_RIGHT;
            }
            case CONCENTRATE -> SwarmRole.CHASER;
            case BASELINE, REGROUP, SEARCH -> baseline;
        };
    }

    private SwarmAiRoleBiasPolicy() {}
}
