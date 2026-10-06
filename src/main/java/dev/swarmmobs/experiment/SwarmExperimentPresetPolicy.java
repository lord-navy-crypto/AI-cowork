package dev.swarmmobs.experiment;

public final class SwarmExperimentPresetPolicy {

    public record Settings(
            boolean sensingEnabled,
            double sensingDropout,
            double sensingNoise,
            boolean communicationEnabled,
            int communicationLatencyTicks,
            double communicationDropout,
            double communicationRadius,
            int stuckWindowTicks,
            double recoveryDistance,
            double congestionPenalty,
            boolean pathEvidenceEnabled
    ) {}

    public static Settings settings(SwarmExperimentPreset preset) {
        SwarmExperimentPreset selected = preset == null
                ? SwarmExperimentPreset.BASELINE
                : preset;

        return switch (selected) {
            case BASELINE -> new Settings(
                    false, 0.0, 0.0,
                    true, 0, 0.0, 16.0,
                    24, 2.0, 0.75, true
            );
            case NOISY_SENSING -> new Settings(
                    true, 0.20, 1.00,
                    true, 0, 0.0, 16.0,
                    24, 2.0, 0.75, true
            );
            case LOSSY_COMMS -> new Settings(
                    false, 0.0, 0.0,
                    true, 8, 0.25, 12.0,
                    24, 2.0, 0.75, true
            );
            case COMBINED_FAULTS -> new Settings(
                    true, 0.20, 1.00,
                    true, 8, 0.25, 12.0,
                    24, 2.0, 0.75, true
            );
            case NAVIGATION_STRESS -> new Settings(
                    false, 0.0, 0.0,
                    true, 0, 0.0, 16.0,
                    14, 2.75, 1.25, true
            );
        };
    }

    private SwarmExperimentPresetPolicy() {}
}
