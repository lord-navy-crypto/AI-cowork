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
                2L,
                5L,
                3L,
                1L,
                90L,
                1,
                7L,
                4L,
                2,
                6L,
                5L,
                4L,
                3L,
                3L
        );

        assertEquals(0.20, snapshot.communicationDropRate(), 1.0e-9);
        assertEquals(0.25, snapshot.recoveryFailureRate(), 1.0e-9);
        assertEquals(0.75, snapshot.searchSuccessRate(), 1.0e-9);
        assertEquals(30.0, snapshot.averageReacquisitionTicks(), 1.0e-9);
        assertEquals(7L, snapshot.engineeringBlocksBroken());
        assertEquals(4L, snapshot.engineeringBlocksPlaced());
        assertEquals(2, snapshot.carriedEngineeringBlocks());
        assertEquals(6L, snapshot.engineeringRequestsPublished());
        assertEquals(5L, snapshot.engineeringTasksClaimed());
        assertEquals(4L, snapshot.engineeringTasksCompleted());
        assertEquals(3L, snapshot.engineeringMaterialsGiven());
        assertEquals(3L, snapshot.engineeringMaterialsReceived());
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
                0L,
                0L,
                0L,
                0L,
                0L,
                0,
                0L,
                0L,
                0,
                0L,
                0L,
                0L,
                0L,
                0L
        );

        assertEquals(0.0, snapshot.communicationDropRate(), 1.0e-9);
        assertEquals(0.0, snapshot.recoveryFailureRate(), 1.0e-9);
        assertEquals(0.0, snapshot.searchSuccessRate(), 1.0e-9);
        assertEquals(0.0, snapshot.averageReacquisitionTicks(), 1.0e-9);
    }
}
