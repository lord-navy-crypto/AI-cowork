package dev.swarmmobs.ai;

/**
 * Compact, aggregate swarm snapshot intended for slow high-level strategy decisions.
 *
 * It intentionally contains no per-tick movement commands and no world coordinates.
 */
public record SwarmStrategyRequest(
        long gameTick,
        int agentCount,
        int targetKnownCount,
        int directObservationCount,
        double averageNeighborCount,
        double averageSeparation,
        double averageCohesion,
        int pendingMessages,
        long deliveredMessages,
        long droppedMessages
) {
    public static SwarmStrategyRequest demo() {
        return new SwarmStrategyRequest(
                1200L,
                8,
                6,
                2,
                4.25,
                0.38,
                0.61,
                3,
                19,
                2
        );
    }
}
