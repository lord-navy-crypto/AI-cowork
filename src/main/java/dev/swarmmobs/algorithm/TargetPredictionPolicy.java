package dev.swarmmobs.algorithm;

/**
 * Conservative short-horizon prediction based only on velocity stored in a truthful
 * TargetObservation snapshot.
 *
 * It never queries the live target and hard-caps both horizon and spatial offset.
 */
public final class TargetPredictionPolicy {

    public record Prediction(
            double x,
            double z,
            double offsetMagnitude,
            double effectivePredictionTicks
    ) {}

    public static Prediction predict(
            TargetObservation observation,
            long currentTick,
            double confidence,
            int leadTicks,
            int maxPredictionTicks,
            double maxPredictionDistance
    ) {
        if (observation == null || !observation.hasFinitePosition()) {
            return new Prediction(Double.NaN, Double.NaN, 0.0, 0.0);
        }

        double normalizedConfidence = clamp(confidence, 0.0, 1.0);
        if (!observation.hasFiniteVelocity()
                || observation.horizontalSpeed() < 1.0e-9
                || normalizedConfidence <= 0.0) {
            return new Prediction(
                    observation.x(),
                    observation.z(),
                    0.0,
                    0.0
            );
        }

        long age = Math.max(0L, currentTick - observation.observationTick());
        int horizonCap = Math.max(0, maxPredictionTicks);
        double requestedTicks = Math.min(
                horizonCap,
                age + Math.max(0, leadTicks)
        );

        double effectiveTicks = requestedTicks * normalizedConfidence;
        double dx = observation.velocityX() * effectiveTicks;
        double dz = observation.velocityZ() * effectiveTicks;

        double maxDistance = Math.max(0.0, maxPredictionDistance);
        double offset = Math.hypot(dx, dz);
        if (offset > maxDistance && offset > 1.0e-9) {
            double scale = maxDistance / offset;
            dx *= scale;
            dz *= scale;
            offset = maxDistance;
        }

        return new Prediction(
                observation.x() + dx,
                observation.z() + dz,
                offset,
                effectiveTicks
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private TargetPredictionPolicy() {}
}
