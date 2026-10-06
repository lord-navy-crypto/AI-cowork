package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmLocalComposition;

/**
 * Keeps ranged-support agents clear of an active breacher ingress lane.
 */
public final class SwarmSupportSpacingPolicy {
    public static final double BREACHER_SUPPORT_STANDOFF_MULTIPLIER = 1.18;

    public static double formationRadiusMultiplier(
            SwarmAgentArchetype archetype,
            SwarmLocalComposition composition
    ) {
        if (archetype == SwarmAgentArchetype.RANGED_SUPPORT
                && composition != null
                && composition.hasBreacher()) {
            return BREACHER_SUPPORT_STANDOFF_MULTIPLIER;
        }
        return 1.0;
    }

    private SwarmSupportSpacingPolicy() {}
}
