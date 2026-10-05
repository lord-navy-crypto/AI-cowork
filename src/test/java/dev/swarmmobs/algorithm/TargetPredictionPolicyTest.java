package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TargetPredictionPolicyTest {

    @Test
    void freshMovingTargetReceivesShortLead() {
        TargetObservation observation = movingObservation(100L, 0.20, 0.0);

        var prediction = TargetPredictionPolicy.predict(
                observation,
                100L,
                1.0,
                6,
                12,
                3.5
        );

        assertEquals(6.0, prediction.effectivePredictionTicks(), 1.0e-9);
        assertEquals(1.2, prediction.offsetMagnitude(), 1.0e-9);
        assertEquals(11.2, prediction.x(), 1.0e-9);
    }

    @Test
    void confidenceReducesPredictionStrength() {
        TargetObservation observation = movingObservation(100L, 0.20, 0.0);

        var high = TargetPredictionPolicy.predict(
                observation, 106L, 1.0, 6, 12, 3.5
        );
        var low = TargetPredictionPolicy.predict(
                observation, 106L, 0.25, 6, 12, 3.5
        );

        assertTrue(low.offsetMagnitude() < high.offsetMagnitude());
        assertTrue(low.effectivePredictionTicks() < high.effectivePredictionTicks());
    }

    @Test
    void predictionDistanceIsHardCapped() {
        TargetObservation observation = movingObservation(100L, 2.0, 0.0);

        var prediction = TargetPredictionPolicy.predict(
                observation, 112L, 1.0, 6, 12, 3.5
        );

        assertEquals(3.5, prediction.offsetMagnitude(), 1.0e-9);
        assertEquals(13.5, prediction.x(), 1.0e-9);
    }

    @Test
    void stationaryTargetDoesNotCreateSyntheticMotion() {
        TargetObservation observation = movingObservation(100L, 0.0, 0.0);

        var prediction = TargetPredictionPolicy.predict(
                observation, 110L, 1.0, 6, 12, 3.5
        );

        assertEquals(0.0, prediction.offsetMagnitude(), 1.0e-9);
        assertEquals(observation.x(), prediction.x(), 1.0e-9);
        assertEquals(observation.z(), prediction.z(), 1.0e-9);
    }

    private static TargetObservation movingObservation(
            long tick,
            double velocityX,
            double velocityZ
    ) {
        return new TargetObservation(
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                tick,
                10.0,
                64.0,
                5.0,
                1.0,
                0.0,
                velocityX,
                velocityZ
        );
    }
}
