package dev.swarmmobs.algorithm;

import java.util.UUID;

/**
 * Immutable last-known target snapshot.
 *
 * Swarm members and communication messages carry this snapshot instead of resolving
 * a target UUID back to the player's live position. This preserves partial observability:
 * stale information remains spatially stale.
 */
public record TargetObservation(
        UUID targetId,
        long observationTick,
        double x,
        double y,
        double z,
        double forwardX,
        double forwardZ
) {
    public TargetObservation {
        if (targetId == null) {
            throw new IllegalArgumentException("targetId cannot be null");
        }
    }

    public static TargetObservation unknownPosition(UUID targetId, long observationTick) {
        return new TargetObservation(
                targetId,
                observationTick,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN
        );
    }

    public boolean hasFinitePosition() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public double confidence(long currentTick, int memoryTicks) {
        long age = currentTick - observationTick;
        if (age < 0) {
            return 0.0;
        }

        int horizon = Math.max(1, memoryTicks);
        if (age >= horizon) {
            return 0.0;
        }

        return 1.0 - (age / (double) horizon);
    }
}
