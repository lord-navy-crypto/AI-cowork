package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TargetPredictionPolicyTest {

    @Test
    void zeroOrUnknownVelocityKeepsAnchorAtObservedPosition() {
        TargetObservation observation = new TargetObservation(
                UUID.randomUUID(),
                100L,
                10.0,
                64.0,
                5.0,
                1.0,
                0.0
        );

        var prediction = TargetPredictionPolicy.predict(
                observation,
                120L,
                0.5,
                30,
                6.0
        );

        assertEquals(10.0, prediction.anchor().x(), 1.0e-9);
        assertEquals(5.0, prediction.anchor().z(), 1.0e-9);
        assertEquals(0.0, prediction.offsetMagnitude(), 1.0e-9);
    }

    @Test
    void predictionUsesOnlyObservedVelocityAndIsBounded() {
        TargetObservation observation = new TargetObservation(
                UUID.randomUUID(),
                100L,
                10.0,
                64.0,
                5.0,
                1.0,
                0.0,
                1.0,
                0.0
        );

        var prediction = TargetPredictionPolicy.predict(
                observation,
                200L,
                1.0,
                30,
                6.0
        );

        assertEquals(16.0, prediction.anchor().x(), 1.0e-9);
        assertEquals(5.0, prediction.anchor().z(), 1.0e-9);
        assertEquals(6.0, prediction.offsetMagnitude(), 1.0e-9);
        assertEquals(30L, prediction.predictionTicks());
    }

    @Test
    void lowerConfidenceReducesDeadReckoningTrust() {
        TargetObservation observation = new TargetObservation(
                UUID.randomUUID(),
                100L,
                0.0,
                64.0,
                0.0,
                1.0,
                0.0,
                0.2,
                0.0
        );

        var high = TargetPredictionPolicy.predict(
                observation,
                120L,
                0.8,
                30,
                20.0
        );
        var low = TargetPredictionPolicy.predict(
                observation,
                120L,
                0.2,
                30,
                20.0
        );

        assertTrue(high.offsetMagnitude() > low.offsetMagnitude());
        assertEquals(3.2, high.offsetMagnitude(), 1.0e-9);
        assertEquals(0.8, low.offsetMagnitude(), 1.0e-9);
    }

    @Test
    void futureObservationDoesNotProjectBackward() {
        TargetObservation observation = new TargetObservation(
                UUID.randomUUID(),
                200L,
                3.0,
                64.0,
                4.0,
                0.0,
                1.0,
                1.0,
                1.0
        );

        var prediction = TargetPredictionPolicy.predict(
                observation,
                190L,
                1.0,
                30,
                6.0
        );

        assertEquals(0L, prediction.predictionTicks());
        assertEquals(0.0, prediction.offsetMagnitude(), 1.0e-9);
        assertEquals(3.0, prediction.anchor().x(), 1.0e-9);
        assertEquals(4.0, prediction.anchor().z(), 1.0e-9);
    }
}
