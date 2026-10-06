package dev.swarmmobs.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmExperimentPresetPolicyTest {

    @Test
    void baselineHasPerfectSensingAndCommunication() {
        var settings = SwarmExperimentPresetPolicy.settings(SwarmExperimentPreset.BASELINE);

        assertFalse(settings.sensingEnabled());
        assertEquals(0.0, settings.sensingDropout(), 1.0e-9);
        assertEquals(0.0, settings.communicationDropout(), 1.0e-9);
        assertEquals(0, settings.communicationLatencyTicks());
    }

    @Test
    void combinedFaultsAffectBothSensingAndCommunication() {
        var settings = SwarmExperimentPresetPolicy.settings(SwarmExperimentPreset.COMBINED_FAULTS);

        assertTrue(settings.sensingEnabled());
        assertTrue(settings.sensingDropout() > 0.0);
        assertTrue(settings.sensingNoise() > 0.0);
        assertTrue(settings.communicationDropout() > 0.0);
        assertTrue(settings.communicationLatencyTicks() > 0);
    }

    @Test
    void navigationStressChangesRecoveryAndCongestionWithoutAddingSensorFaults() {
        var baseline = SwarmExperimentPresetPolicy.settings(SwarmExperimentPreset.BASELINE);
        var stress = SwarmExperimentPresetPolicy.settings(SwarmExperimentPreset.NAVIGATION_STRESS);

        assertFalse(stress.sensingEnabled());
        assertTrue(stress.stuckWindowTicks() < baseline.stuckWindowTicks());
        assertTrue(stress.recoveryDistance() > baseline.recoveryDistance());
        assertTrue(stress.congestionPenalty() > baseline.congestionPenalty());
    }
}
