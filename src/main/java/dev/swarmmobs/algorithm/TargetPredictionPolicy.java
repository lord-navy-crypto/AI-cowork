package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

/**
 * Bounded dead-reckoning from a real target observation.
 *
 * The prediction uses only velocity captured at the observation tick. It never reads
 * a live target position while occluded. Prediction trust falls with target confidence.
 */
public final class TargetPredictionPolicy {

    public record Prediction(
            Vec2 anchor,
            double offsetMagnitude,
            long predictionTicks
    ) {}

    public static Prediction predict(
            TargetObservation observation,
            long currentTick,
            double confidence,
            int maxPredictionTicks,
            double maxPredictionDistance
    ) {
        Vec2 base = new Vec2(observation.x(), observation.z());

        if (!observation.hasFiniteVelocity()) {
            return new Prediction(base, 0.0, 0L);
        }

        long age = Math.max(0L, currentTick - observation.observationTick());
        long predictionTicks = Math.min(age, Math.max(0, maxPredictionTicks));
        double trust = Math.max(0.0, Math.min(1.0, confidence));

        Vec2 rawOffset = new Vec2(
                observation.velocityX() * predictionTicks * trust,
                observation.velocityZ() * predictionTicks * trust
        );

        Vec2 offset = SwarmCombatPlanner.clampLength(
                rawOffset,
                Math.max(0.0, maxPredictionDistance)
        );

        return new Prediction(
                base.add(offset),
                offset.length(),
                predictionTicks
        );
    }

    private TargetPredictionPolicy() {}
}
