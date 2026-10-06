package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmSensingPolicyTest {
    private static final UUID OBSERVER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000022");

    @Test
    void zeroFaultsPreserveExactPosition() {
        var sample = SwarmSensingPolicy.samplePosition(
                OBSERVER,
                TARGET,
                100L,
                10.0,
                64.0,
                -3.0,
                0.0,
                0.0,
                7
        );

        assertTrue(sample.observed());
        assertEquals(10.0, sample.measuredX(), 1.0e-9);
        assertEquals(64.0, sample.measuredY(), 1.0e-9);
        assertEquals(-3.0, sample.measuredZ(), 1.0e-9);
        assertEquals(0.0, sample.horizontalNoiseMagnitude(), 1.0e-9);
    }

    @Test
    void fullDropoutAlwaysRejectsObservation() {
        var sample = SwarmSensingPolicy.samplePosition(
                OBSERVER,
                TARGET,
                100L,
                1.0,
                2.0,
                3.0,
                1.0,
                2.0,
                7
        );

        assertFalse(sample.observed());
    }

    @Test
    void noiseIsBoundedAndKeepsVerticalMeasurementTruthful() {
        double limit = 1.5;
        var sample = SwarmSensingPolicy.samplePosition(
                OBSERVER,
                TARGET,
                101L,
                10.0,
                70.0,
                5.0,
                0.0,
                limit,
                9
        );

        assertTrue(sample.observed());
        assertEquals(70.0, sample.measuredY(), 1.0e-9);
        assertTrue(Math.abs(sample.measuredX() - 10.0) <= limit + 1.0e-9);
        assertTrue(Math.abs(sample.measuredZ() - 5.0) <= limit + 1.0e-9);
        assertTrue(sample.horizontalNoiseMagnitude() <= Math.sqrt(2.0) * limit + 1.0e-9);
    }

    @Test
    void identicalInputsAreExactlyReproducible() {
        var first = SwarmSensingPolicy.samplePosition(
                OBSERVER, TARGET, 123L,
                8.0, 65.0, -2.0,
                0.35, 1.2, 42
        );
        var second = SwarmSensingPolicy.samplePosition(
                OBSERVER, TARGET, 123L,
                8.0, 65.0, -2.0,
                0.35, 1.2, 42
        );

        assertEquals(first, second);
    }

    @Test
    void changingSeedChangesDeterministicNoiseSample() {
        var first = SwarmSensingPolicy.samplePosition(
                OBSERVER, TARGET, 200L,
                0.0, 64.0, 0.0,
                0.0, 2.0, 1
        );
        var second = SwarmSensingPolicy.samplePosition(
                OBSERVER, TARGET, 200L,
                0.0, 64.0, 0.0,
                0.0, 2.0, 2
        );

        assertTrue(first.observed());
        assertTrue(second.observed());
        assertNotEquals(first.measuredX(), second.measuredX());
    }
}
