package dev.swarmmobs.agent;

public record SwarmAgentProfile(
        SwarmAgentArchetype archetype,
        double formationRadiusMultiplier,
        double moveSpeedMultiplier,
        double arrivalTolerance
) {
    public static SwarmAgentProfile assault() {
        return new SwarmAgentProfile(
                SwarmAgentArchetype.ASSAULT,
                1.0,
                1.0,
                0.0
        );
    }

    public static SwarmAgentProfile rangedSupport() {
        return new SwarmAgentProfile(
                SwarmAgentArchetype.RANGED_SUPPORT,
                1.75,
                0.95,
                1.75
        );
    }
}
