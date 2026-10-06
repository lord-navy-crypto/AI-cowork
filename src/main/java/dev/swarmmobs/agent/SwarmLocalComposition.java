package dev.swarmmobs.agent;

import java.util.Collection;

/**
 * Local, decentralized view of the capability mix around one swarm agent.
 */
public record SwarmLocalComposition(
        int assaultCount,
        int rangedSupportCount,
        int flankerCount,
        int breacherCount
) {
    public SwarmLocalComposition(
            int assaultCount,
            int rangedSupportCount,
            int flankerCount
    ) {
        this(assaultCount, rangedSupportCount, flankerCount, 0);
    }
    public static SwarmLocalComposition fromArchetypes(
            SwarmAgentArchetype self,
            Collection<SwarmAgentArchetype> neighbors
    ) {
        int assault = self == SwarmAgentArchetype.ASSAULT ? 1 : 0;
        int ranged = self == SwarmAgentArchetype.RANGED_SUPPORT ? 1 : 0;
        int flankers = self == SwarmAgentArchetype.FLANKER ? 1 : 0;
        int breachers = self == SwarmAgentArchetype.BREACHER ? 1 : 0;

        if (neighbors != null) {
            for (SwarmAgentArchetype archetype : neighbors) {
                if (archetype == null) {
                    continue;
                }
                switch (archetype) {
                    case ASSAULT -> assault++;
                    case RANGED_SUPPORT -> ranged++;
                    case FLANKER -> flankers++;
                    case BREACHER -> breachers++;
                }
            }
        }

        return new SwarmLocalComposition(assault, ranged, flankers, breachers);
    }

    public boolean dedicatedFlankCoverage() {
        return flankerCount >= 2;
    }

    public boolean hasBreacher() {
        return breacherCount > 0;
    }
}
