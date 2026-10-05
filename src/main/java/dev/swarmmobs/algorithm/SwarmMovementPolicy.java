package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmBehaviorMode;

/**
 * Converts target confidence and behavior mode into a navigation speed factor.
 *
 * ENGAGE keeps the conservative confidence-based slowdown used for stale target data.
 * SEARCH uses a dedicated factor so expanding coverage does not become progressively
 * slower just because the target observation is old.
 */
public final class SwarmMovementPolicy {

    public static double speedFactor(
            SwarmBehaviorMode mode,
            double confidence,
            double staleTargetMinSpeedFactor,
            double searchSpeedFactor
    ) {
        if (mode == SwarmBehaviorMode.SEARCH) {
            return Math.max(0.0, searchSpeedFactor);
        }

        double normalizedConfidence = clamp(confidence, 0.0, 1.0);
        double minFactor = clamp(staleTargetMinSpeedFactor, 0.0, 1.0);
        return minFactor + (1.0 - minFactor) * normalizedConfidence;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private SwarmMovementPolicy() {}
}
