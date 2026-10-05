package dev.swarmmobs.algorithm;

import java.util.UUID;

/**
 * Immutable last-known target snapshot.
 *
 * Velocity is captured only at a real observation and then relayed unchanged.
 * Prediction therefore operates on stale observed motion rather than hidden live state.
 */
public record TargetObservation(
        UUID targetId,
        long observationTick,
        double x,
        double y,
        double z,
        double forwardX,
        double forwardZ,
        double velocityX,
        double velocityZ
) {
    public TargetObservation {
        if (targetId == null) {
            throw new IllegalArgumentException("targetId cannot be null");
        }
    }

    public TargetObservation(
            UUID targetId,
            long observationTick,
            double x,
            double y,
            double z,
            double forwardX,
            double forwardZ
    ) {
        this(
                targetId,
                observationTick,
                x,
                y,
                z,
                forwardX,
                forwardZ,
                0.0,
                0.0
        );
    }

    public static TargetObservation unknownPosition(UUID targetId, long observationTick) {
        return new TargetObservation(
                targetId,
                observationTick,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                0.0,
                0.0
        );
    }

    public boolean hasFinitePosition() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public boolean hasFiniteVelocity() {
        return Double.isFinite(velocityX) && Double.isFinite(velocityZ);
    }

    public double horizontalSpeed() {
        return hasFiniteVelocity() ? Math.hypot(velocityX, velocityZ) : 0.0;
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
