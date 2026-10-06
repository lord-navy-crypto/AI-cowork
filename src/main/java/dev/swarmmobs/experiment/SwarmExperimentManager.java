package dev.swarmmobs.experiment;

import dev.swarmmobs.config.SwarmConfig;

public final class SwarmExperimentManager {
    private static SwarmExperimentPreset activePreset = SwarmExperimentPreset.BASELINE;
    private static int experimentSeed = 42;

    public static SwarmExperimentPreset activePreset() {
        return activePreset;
    }

    public static int experimentSeed() {
        return experimentSeed;
    }

    public static void setExperimentSeed(int seed) {
        experimentSeed = seed;
        SwarmConfig.SENSING_EXPERIMENT_SEED.set(seed);
        SwarmConfig.COMMUNICATION_EXPERIMENT_SEED.set(seed);
    }

    public static void apply(SwarmExperimentPreset preset) {
        SwarmExperimentPreset selected = preset == null
                ? SwarmExperimentPreset.BASELINE
                : preset;
        var settings = SwarmExperimentPresetPolicy.settings(selected);

        // Restore the known-good coordination/search/local-planning baseline first.
        SwarmConfig.ENABLED.set(true);
        SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.set(20);
        SwarmConfig.ROLE_HYSTERESIS_TICKS.set(12);

        SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.set(0.45);
        SwarmConfig.SEARCH_SPEED_FACTOR.set(1.0);
        SwarmConfig.SEARCH_MAX_RADIUS.set(10.0);
        SwarmConfig.TARGET_PREDICTION_ENABLED.set(true);
        SwarmConfig.TARGET_PREDICTION_MAX_DISTANCE.set(3.5);

        SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(true);
        SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.set(1.5);
        SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.set(1.5);
        SwarmConfig.NAV_WALKABILITY_ENABLED.set(true);
        SwarmConfig.NAV_MAX_PROBE_DROP_BLOCKS.set(1);
        SwarmConfig.NAV_STUCK_MIN_PROGRESS.set(0.75);
        SwarmConfig.NAV_RECOVERY_DURATION_TICKS.set(18);
        SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.set(1.0);
        SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.set(0.20);
        SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.set(2.5);
        SwarmConfig.NAV_PATH_NODE_PENALTY.set(0.05);
        SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.set(0.25);
        SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.set(1.5);

        SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(settings.sensingEnabled());
        SwarmConfig.SENSING_DROPOUT_RATE.set(settings.sensingDropout());
        SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.set(settings.sensingNoise());

        SwarmConfig.COMMUNICATION_ENABLED.set(settings.communicationEnabled());
        SwarmConfig.COMMUNICATION_LATENCY_TICKS.set(settings.communicationLatencyTicks());
        SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(settings.communicationDropout());
        SwarmConfig.COMMUNICATION_RADIUS.set(settings.communicationRadius());

        SwarmConfig.NAV_STUCK_WINDOW_TICKS.set(settings.stuckWindowTicks());
        SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.set(settings.recoveryDistance());
        SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.set(settings.congestionPenalty());
        SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(settings.pathEvidenceEnabled());

        SwarmConfig.SENSING_EXPERIMENT_SEED.set(experimentSeed);
        SwarmConfig.COMMUNICATION_EXPERIMENT_SEED.set(experimentSeed);
        activePreset = selected;
    }

    private SwarmExperimentManager() {}
}
