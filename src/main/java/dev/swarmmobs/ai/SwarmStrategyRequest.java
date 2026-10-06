package dev.swarmmobs.ai;

public record SwarmStrategyRequest(
        long gameTick,
        int agentCount,
        String experimentPreset,
        boolean experimentActive,
        long elapsedExperimentTicks,
        double observedCommunicationDropRate,
        double configuredCommunicationDropRate,
        int communicationLatencyTicks,
        double sensingDropoutRate,
        double sensingHorizontalNoise,
        long obstacleDetours,
        long recoveries,
        double recoveryFailureRate,
        long pathQueries,
        long roleReassignments,
        int activeSearchEpisodes,
        double searchSuccessRate,
        double averageReacquisitionTicks
) {}
