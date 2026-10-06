package dev.swarmmobs.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmExperimentMetricsTest {

    @Test
    void snapshotComputesObservedRatesSafely() {
        var snapshot = new SwarmExperimentMetrics.Snapshot(
                true,
                200L,
                8,
                100L,
                80L,
                20L,
                5L,
                3L,
                4L,
                1L,
                12L,
                2L
        );

        assertEquals(0.20, snapshot.communicationDropRate(), 1.0e-9);
        assertEquals(0.25, snapshot.recoveryFailureRate(), 1.0e-9);
    }

    @Test
    void zeroDenominatorsProduceZeroRates() {
        var snapshot = new SwarmExperimentMetrics.Snapshot(
                false,
                0L,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L
        );

        assertEquals(0.0, snapshot.communicationDropRate(), 1.0e-9);
        assertEquals(0.0, snapshot.recoveryFailureRate(), 1.0e-9);
    }
}
