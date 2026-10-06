package dev.swarmmobs.network;

import dev.swarmmobs.client.SwarmControlClient;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.experiment.SwarmExperimentManager;
import dev.swarmmobs.experiment.SwarmExperimentPreset;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

public final class SwarmControlNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(
                ControlPanelActionPayload.TYPE,
                ControlPanelActionPayload.STREAM_CODEC,
                SwarmControlNetwork::handleAction
        );

        registrar.playToClient(
                ControlPanelSnapshotPayload.TYPE,
                ControlPanelSnapshotPayload.STREAM_CODEC,
                SwarmControlNetwork::handleSnapshot
        );
    }

    public static void sendSnapshot(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
                player,
                new ControlPanelSnapshotPayload(snapshotData())
        );
    }

    private static void handleAction(
            ControlPanelActionPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
            return;
        }

        applyAction(payload.action(), payload.value());
        sendSnapshot(player);
    }

    private static void handleSnapshot(
            ControlPanelSnapshotPayload payload,
            IPayloadContext context
    ) {
        SwarmControlClient.acceptSnapshot(payload.data());
    }

    private static void applyAction(String action, double value) {
        if (!action.startsWith("preset_") && !action.equals("experiment_seed_delta")) {
            SwarmExperimentManager.markCustom();
        }

        switch (action) {
            case "toggle_master" -> SwarmConfig.ENABLED.set(!SwarmConfig.ENABLED.get());

            case "preset_baseline" -> SwarmExperimentManager.apply(SwarmExperimentPreset.BASELINE);
            case "preset_noisy_sensing" -> SwarmExperimentManager.apply(SwarmExperimentPreset.NOISY_SENSING);
            case "preset_lossy_comms" -> SwarmExperimentManager.apply(SwarmExperimentPreset.LOSSY_COMMS);
            case "preset_combined_faults" -> SwarmExperimentManager.apply(SwarmExperimentPreset.COMBINED_FAULTS);
            case "preset_navigation_stress" -> SwarmExperimentManager.apply(SwarmExperimentPreset.NAVIGATION_STRESS);
            case "experiment_seed_delta" -> {
                long next = (long) SwarmExperimentManager.experimentSeed() + Math.round(value);
                int seed = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, next));
                SwarmExperimentManager.setExperimentSeed(seed);
            }

            case "formation_hysteresis_delta" -> SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.set((int) clamp(
                    SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.get() + value,
                    0.0,
                    200.0
            ));
            case "role_hysteresis_delta" -> SwarmConfig.ROLE_HYSTERESIS_TICKS.set((int) clamp(
                    SwarmConfig.ROLE_HYSTERESIS_TICKS.get() + value,
                    0.0,
                    200.0
            ));
            case "coord_baseline" -> {
                SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.set(20);
                SwarmConfig.ROLE_HYSTERESIS_TICKS.set(12);
            }

            case "toggle_sensing" ->
                    SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(!SwarmConfig.SENSING_IMPERFECTION_ENABLED.get());
            case "sensing_drop_delta" -> {
                SwarmConfig.SENSING_DROPOUT_RATE.set(clamp(
                        SwarmConfig.SENSING_DROPOUT_RATE.get() + value,
                        0.0,
                        1.0
                ));
                SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(true);
            }
            case "sensing_noise_delta" -> {
                SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.set(clamp(
                        SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.get() + value,
                        0.0,
                        8.0
                ));
                SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(true);
            }
            case "sensing_baseline" -> {
                SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(false);
                SwarmConfig.SENSING_DROPOUT_RATE.set(0.0);
                SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.set(0.0);
            }

            case "toggle_comm" ->
                    SwarmConfig.COMMUNICATION_ENABLED.set(!SwarmConfig.COMMUNICATION_ENABLED.get());
            case "comm_drop_delta" -> SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(clamp(
                    SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.get() + value,
                    0.0,
                    1.0
            ));
            case "comm_latency_delta" -> SwarmConfig.COMMUNICATION_LATENCY_TICKS.set((int) clamp(
                    SwarmConfig.COMMUNICATION_LATENCY_TICKS.get() + value,
                    0.0,
                    400.0
            ));
            case "comm_radius_delta" -> SwarmConfig.COMMUNICATION_RADIUS.set(clamp(
                    SwarmConfig.COMMUNICATION_RADIUS.get() + value,
                    1.0,
                    96.0
            ));
            case "comm_baseline" -> {
                SwarmConfig.COMMUNICATION_ENABLED.set(true);
                SwarmConfig.COMMUNICATION_LATENCY_TICKS.set(0);
                SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(0.0);
                SwarmConfig.COMMUNICATION_RADIUS.set(16.0);
            }

            case "search_threshold_delta" -> SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.set(clamp(
                    SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.get() + value,
                    0.05,
                    0.95
            ));
            case "search_speed_delta" -> SwarmConfig.SEARCH_SPEED_FACTOR.set(clamp(
                    SwarmConfig.SEARCH_SPEED_FACTOR.get() + value,
                    0.25,
                    1.5
            ));
            case "search_max_radius_delta" -> SwarmConfig.SEARCH_MAX_RADIUS.set(clamp(
                    SwarmConfig.SEARCH_MAX_RADIUS.get() + value,
                    2.0,
                    32.0
            ));
            case "toggle_prediction" ->
                    SwarmConfig.TARGET_PREDICTION_ENABLED.set(!SwarmConfig.TARGET_PREDICTION_ENABLED.get());
            case "prediction_distance_delta" -> SwarmConfig.TARGET_PREDICTION_MAX_DISTANCE.set(clamp(
                    SwarmConfig.TARGET_PREDICTION_MAX_DISTANCE.get() + value,
                    0.0,
                    12.0
            ));
            case "search_baseline" -> {
                SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.set(0.45);
                SwarmConfig.SEARCH_SPEED_FACTOR.set(1.0);
                SwarmConfig.SEARCH_MAX_RADIUS.set(10.0);
                SwarmConfig.TARGET_PREDICTION_ENABLED.set(true);
                SwarmConfig.TARGET_PREDICTION_MAX_DISTANCE.set(3.5);
            }

            case "toggle_obstacle" ->
                    SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(!SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.get());
            case "nav_lookahead_delta" -> SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.set(clamp(
                    SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.get() + value,
                    0.5,
                    4.0
            ));
            case "nav_lateral_delta" -> SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.set(clamp(
                    SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.get() + value,
                    0.5,
                    4.0
            ));
            case "toggle_walkability" ->
                    SwarmConfig.NAV_WALKABILITY_ENABLED.set(!SwarmConfig.NAV_WALKABILITY_ENABLED.get());
            case "nav_max_drop_delta" -> SwarmConfig.NAV_MAX_PROBE_DROP_BLOCKS.set((int) clamp(
                    SwarmConfig.NAV_MAX_PROBE_DROP_BLOCKS.get() + value,
                    0.0,
                    4.0
            ));
            case "nav_recovery_duration_delta" -> SwarmConfig.NAV_RECOVERY_DURATION_TICKS.set((int) clamp(
                    SwarmConfig.NAV_RECOVERY_DURATION_TICKS.get() + value,
                    3.0,
                    100.0
            ));
            case "nav_stuck_window_delta" -> SwarmConfig.NAV_STUCK_WINDOW_TICKS.set((int) clamp(
                    SwarmConfig.NAV_STUCK_WINDOW_TICKS.get() + value,
                    6.0,
                    200.0
            ));
            case "nav_stuck_progress_delta" -> SwarmConfig.NAV_STUCK_MIN_PROGRESS.set(clamp(
                    SwarmConfig.NAV_STUCK_MIN_PROGRESS.get() + value,
                    0.05,
                    4.0
            ));
            case "nav_recovery_distance_delta" -> SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.set(clamp(
                    SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.get() + value,
                    0.25,
                    6.0
            ));
            case "nav_progress_weight_delta" -> SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.set(clamp(
                    SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.get() + value,
                    0.0,
                    4.0
            ));
            case "nav_lateral_penalty_delta" -> SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.set(clamp(
                    SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.get() + value,
                    0.0,
                    4.0
            ));
            case "nav_congestion_penalty_delta" -> SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.set(clamp(
                    SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.get() + value,
                    0.0,
                    4.0
            ));
            case "nav_congestion_radius_delta" -> SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.set(clamp(
                    SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.get() + value,
                    0.5,
                    8.0
            ));
            case "toggle_path_evidence" ->
                    SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(!SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get());
            case "nav_path_node_penalty_delta" -> SwarmConfig.NAV_PATH_NODE_PENALTY.set(clamp(
                    SwarmConfig.NAV_PATH_NODE_PENALTY.get() + value,
                    0.0,
                    2.0
            ));
            case "nav_path_residual_penalty_delta" -> SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.set(clamp(
                    SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.get() + value,
                    0.0,
                    2.0
            ));
            case "nav_path_max_residual_delta" -> SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.set(clamp(
                    SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.get() + value,
                    0.0,
                    4.0
            ));
            case "nav_baseline" -> {
                SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(true);
                SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.set(1.5);
                SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.set(1.5);
                SwarmConfig.NAV_WALKABILITY_ENABLED.set(true);
                SwarmConfig.NAV_MAX_PROBE_DROP_BLOCKS.set(1);
                SwarmConfig.NAV_STUCK_WINDOW_TICKS.set(24);
                SwarmConfig.NAV_STUCK_MIN_PROGRESS.set(0.75);
                SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.set(2.0);
                SwarmConfig.NAV_RECOVERY_DURATION_TICKS.set(18);
                SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.set(1.0);
                SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.set(0.20);
                SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.set(0.75);
                SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.set(2.5);
                SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(true);
                SwarmConfig.NAV_PATH_NODE_PENALTY.set(0.05);
                SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.set(0.25);
                SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.set(1.5);
            }

            case "baseline_all" -> {
                SwarmExperimentManager.apply(SwarmExperimentPreset.BASELINE);
                SwarmConfig.ENABLED.set(true);
                SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.set(20);
                SwarmConfig.ROLE_HYSTERESIS_TICKS.set(12);

                SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(false);
                SwarmConfig.SENSING_DROPOUT_RATE.set(0.0);
                SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.set(0.0);

                SwarmConfig.COMMUNICATION_ENABLED.set(true);
                SwarmConfig.COMMUNICATION_LATENCY_TICKS.set(0);
                SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(0.0);
                SwarmConfig.COMMUNICATION_RADIUS.set(16.0);

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
                SwarmConfig.NAV_RECOVERY_DURATION_TICKS.set(18);
                SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.set(1.0);
                SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.set(0.20);
                SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.set(0.75);
                SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.set(2.5);
                SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(true);
                SwarmConfig.NAV_PATH_NODE_PENALTY.set(0.05);
                SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.set(0.25);
                SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.set(1.5);
            }

            default -> {
                // Unknown actions are ignored deliberately.
            }
        }
    }

    private static String snapshotData() {
        return String.join(";",
                pair("master", SwarmConfig.ENABLED.get()),
                "activePreset=" + SwarmExperimentManager.activePreset().name(),
                pair("experimentSeed", SwarmExperimentManager.experimentSeed()),
                pair("formationHysteresis", SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.get()),
                pair("roleHysteresis", SwarmConfig.ROLE_HYSTERESIS_TICKS.get()),

                pair("sensingEnabled", SwarmConfig.SENSING_IMPERFECTION_ENABLED.get()),
                pair("sensingDrop", SwarmConfig.SENSING_DROPOUT_RATE.get()),
                pair("sensingNoise", SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.get()),

                pair("commEnabled", SwarmConfig.COMMUNICATION_ENABLED.get()),
                pair("commDrop", SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.get()),
                pair("commLatency", SwarmConfig.COMMUNICATION_LATENCY_TICKS.get()),
                pair("commRadius", SwarmConfig.COMMUNICATION_RADIUS.get()),

                pair("searchThreshold", SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.get()),
                pair("searchSpeed", SwarmConfig.SEARCH_SPEED_FACTOR.get()),
                pair("searchMaxRadius", SwarmConfig.SEARCH_MAX_RADIUS.get()),
                pair("predictionEnabled", SwarmConfig.TARGET_PREDICTION_ENABLED.get()),
                pair("predictionDistance", SwarmConfig.TARGET_PREDICTION_MAX_DISTANCE.get()),

                pair("obstacleEnabled", SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.get()),
                pair("navLookahead", SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.get()),
                pair("navLateral", SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.get()),
                pair("walkabilityEnabled", SwarmConfig.NAV_WALKABILITY_ENABLED.get()),
                pair("navMaxDrop", SwarmConfig.NAV_MAX_PROBE_DROP_BLOCKS.get()),
                pair("stuckWindow", SwarmConfig.NAV_STUCK_WINDOW_TICKS.get()),
                pair("stuckMinProgress", SwarmConfig.NAV_STUCK_MIN_PROGRESS.get()),
                pair("recoveryDistance", SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.get()),
                pair("recoveryDuration", SwarmConfig.NAV_RECOVERY_DURATION_TICKS.get()),
                pair("navProgressWeight", SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.get()),
                pair("navLateralPenalty", SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.get()),
                pair("navCongestionPenalty", SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.get()),
                pair("navCongestionRadius", SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.get()),
                pair("navPathEvidenceEnabled", SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get()),
                pair("navPathNodePenalty", SwarmConfig.NAV_PATH_NODE_PENALTY.get()),
                pair("navPathResidualPenalty", SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.get()),
                pair("navPathMaxResidual", SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.get())
        );
    }

    private static String pair(String key, boolean value) {
        return key + "=" + value;
    }

    private static String pair(String key, int value) {
        return key + "=" + value;
    }

    private static String pair(String key, double value) {
        return key + "=" + String.format(Locale.ROOT, "%.4f", value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private SwarmControlNetwork() {}
}
