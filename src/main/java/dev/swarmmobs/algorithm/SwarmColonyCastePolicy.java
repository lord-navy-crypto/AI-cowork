package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;

/**
 * Insect-inspired flexible idle-colony specialization. These are task biases,
 * not reproductive castes or a magical queen issuing global commands.
 * Engaged members keep their existing combat/engineering specializations.
 */
public final class SwarmColonyCastePolicy {
    public enum Caste { WORKER, GUARD, SCOUT, RESERVE }

    public static Caste idleCaste(SwarmAgentArchetype archetype,
                                  boolean colonyNeedsConstruction,
                                  boolean hostileTargetKnown) {
        if (hostileTargetKnown || archetype == null) {
            return Caste.RESERVE;
        }
        return switch (archetype) {
            case ASSAULT -> colonyNeedsConstruction ? Caste.WORKER : Caste.RESERVE;
            case RANGED_SUPPORT -> Caste.GUARD;
            case FLANKER -> Caste.SCOUT;
            case BREACHER -> Caste.RESERVE;
        };
    }

    private SwarmColonyCastePolicy() {}
}
