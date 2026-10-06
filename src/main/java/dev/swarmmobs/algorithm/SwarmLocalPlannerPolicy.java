package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

import java.util.List;

/**
 * Deterministic local candidate scorer for short-horizon navigation.
 *
 * Terrain feasibility is supplied by the caller. The policy then combines
 * forward progress, lateral cost, and local congestion into one bounded score.
 */
public final class SwarmLocalPlannerPolicy {

    public record Candidate(
            Vec2 waypoint,
            boolean blocked,
            double lateralOffset,
            double congestion,
            boolean pathReachable,
            int pathNodeCount,
            double pathResidualDistance
    ) {}

    public record Choice(
            Vec2 waypoint,
            boolean active,
            int candidateIndex,
            double score
    ) {}

    public static Choice choose(
            Vec2 self,
            Vec2 destination,
            List<Candidate> candidates,
            double progressWeight,
            double lateralPenaltyWeight,
            double congestionPenaltyWeight,
            double pathNodePenaltyWeight,
            double pathResidualPenaltyWeight
    ) {
        if (self == null || destination == null || candidates == null || candidates.isEmpty()) {
            return new Choice(destination, false, -1, Double.NEGATIVE_INFINITY);
        }

        double baselineDistance = destination.subtract(self).length();
        if (baselineDistance < 1.0e-9) {
            return new Choice(destination, false, -1, 0.0);
        }

        double bestScore = Double.NEGATIVE_INFINITY;
        int bestIndex = -1;
        Vec2 bestWaypoint = destination;

        double progressW = Math.max(0.0, progressWeight);
        double lateralW = Math.max(0.0, lateralPenaltyWeight);
        double congestionW = Math.max(0.0, congestionPenaltyWeight);
        double pathNodeW = Math.max(0.0, pathNodePenaltyWeight);
        double pathResidualW = Math.max(0.0, pathResidualPenaltyWeight);

        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate == null
                    || candidate.blocked()
                    || candidate.waypoint() == null
                    || !candidate.pathReachable()) {
                continue;
            }

            double remaining = destination.subtract(candidate.waypoint()).length();
            double progress = baselineDistance - remaining;
            double lateralPenalty = Math.max(0.0, Math.abs(candidate.lateralOffset()));
            double congestionPenalty = Math.max(0.0, candidate.congestion());
            double pathNodePenalty = Math.max(0, candidate.pathNodeCount());
            double pathResidualPenalty = Math.max(0.0, candidate.pathResidualDistance());

            double score = progress * progressW
                    - lateralPenalty * lateralW
                    - congestionPenalty * congestionW
                    - pathNodePenalty * pathNodeW
                    - pathResidualPenalty * pathResidualW;

            if (score > bestScore + 1.0e-9) {
                bestScore = score;
                bestIndex = i;
                bestWaypoint = candidate.waypoint();
            }
        }

        if (bestIndex < 0) {
            return new Choice(destination, false, -1, Double.NEGATIVE_INFINITY);
        }

        return new Choice(bestWaypoint, true, bestIndex, bestScore);
    }

    private SwarmLocalPlannerPolicy() {}
}
