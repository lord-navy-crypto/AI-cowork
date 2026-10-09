package dev.swarmmobs.network;

import dev.swarmmobs.algorithm.SwarmPathBudgetRegistry;
import dev.swarmmobs.algorithm.SwarmNavigationCommandTelemetry;

import dev.swarmmobs.client.SwarmControlClient;
import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmSpecialization;
import dev.swarmmobs.agent.SwarmTaskType;
import dev.swarmmobs.ai.SwarmAiActiveState;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import java.util.EnumMap;
import dev.swarmmobs.ai.SwarmAiShadowService;
import dev.swarmmobs.ai.SwarmAiShadowState;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.colony.SwarmNestScienceTelemetry;
import dev.swarmmobs.experiment.SwarmExperimentManager;
import dev.swarmmobs.experiment.SwarmExperimentMetrics;
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
                new ControlPanelSnapshotPayload(
                        snapshotData(player.serverLevel(), player.hasPermissions(2))
                )
        );
    }

    private static void handleAction(
            ControlPanelActionPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        // Everyone may inspect the server-authoritative panel. Mutations remain
        // permission-gated on the server.
        if ("panel_refresh".equals(payload.action())) {
            sendSnapshot(player);
            return;
        }

        if (!player.hasPermissions(2)) {
            sendSnapshot(player);
            return;
        }

        applyAction(payload.action(), payload.value(), player);
        sendSnapshot(player);
    }

    private static void handleSnapshot(
            ControlPanelSnapshotPayload payload,
            IPayloadContext context
    ) {
        SwarmControlClient.acceptSnapshot(payload.data());
    }

    private static void applyAction(String action, double value, ServerPlayer player) {
        if (!action.startsWith("preset_")
                && !action.startsWith("ai_")
                && !action.equals("experiment_seed_delta")) {
            SwarmExperimentManager.markCustom();
        }

        switch (action) {
            case "toggle_master" -> SwarmConfig.ENABLED.set(!SwarmConfig.ENABLED.get());

            case "ai_toggle" -> {
                boolean next = !SwarmConfig.EXTERNAL_AI_ENABLED.get();
                SwarmConfig.EXTERNAL_AI_ENABLED.set(next);
                if (!next) {
                    SwarmAiActiveState.clear();
                }
            }
            case "ai_shadow" -> SwarmAiShadowService.request(player.serverLevel())
                    .whenComplete((decision, error) ->
                            player.getServer().execute(() -> {
                                if (player.isAlive()) {
                                    sendSnapshot(player);
                                }
                            })
                    );
            case "ai_refresh" -> {
                // Snapshot is returned by the normal server-authoritative refresh below.
            }

            case "preset_baseline" -> SwarmExperimentManager.apply(SwarmExperimentPreset.BASELINE);
            case "preset_noisy_sensing" -> SwarmExperimentManager.apply(SwarmExperimentPreset.NOISY_SENSING);
            case "preset_lossy_comms" -> SwarmExperimentManager.apply(SwarmExperimentPreset.LOSSY_COMMS);
            case "preset_combined_faults" -> SwarmExperimentManager.apply(SwarmExperimentPreset.COMBINED_FAULTS);
            case "preset_navigation_stress" -> SwarmExperimentManager.apply(SwarmExperimentPreset.NAVIGATION_STRESS);
            case "experiment_start" -> SwarmExperimentMetrics.start(player.serverLevel());
            case "experiment_reset" -> SwarmExperimentMetrics.reset(player.serverLevel());
            case "experiment_snapshot" -> {
                // Snapshot is returned by the normal server-authoritative refresh below.
            }

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

            case "toggle_division" ->
                    SwarmConfig.DIVISION_OF_LABOR_ENABLED.set(!SwarmConfig.DIVISION_OF_LABOR_ENABLED.get());
            case "specialization_hold_delta" -> SwarmConfig.SPECIALIZATION_MIN_HOLD_TICKS.set((int) clamp(
                    SwarmConfig.SPECIALIZATION_MIN_HOLD_TICKS.get() + value,
                    0.0,
                    400.0
            ));
            case "specialization_gain_delta" -> SwarmConfig.SPECIALIZATION_EXPERIENCE_GAIN.set(clamp(
                    SwarmConfig.SPECIALIZATION_EXPERIENCE_GAIN.get() + value,
                    0.0,
                    0.25
            ));
            case "specialization_decay_delta" -> SwarmConfig.SPECIALIZATION_EXPERIENCE_DECAY.set(clamp(
                    SwarmConfig.SPECIALIZATION_EXPERIENCE_DECAY.get() + value,
                    0.90,
                    1.0
            ));
            case "labor_baseline" -> {
                SwarmConfig.DIVISION_OF_LABOR_ENABLED.set(true);
                SwarmConfig.SPECIALIZATION_MIN_HOLD_TICKS.set(30);
                SwarmConfig.SPECIALIZATION_EXPERIENCE_GAIN.set(0.025);
                SwarmConfig.SPECIALIZATION_EXPERIENCE_DECAY.set(0.995);
            }

            case "toggle_engineering" ->
                    SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(!SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.get());
            case "engineering_hardness_delta" -> SwarmConfig.ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS.set(clamp(
                    SwarmConfig.ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS.get() + value,
                    0.0,
                    10.0
            ));
            case "engineering_carry_delta" -> SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.set((int) clamp(
                    SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get() + value,
                    0.0,
                    16.0
            ));
            case "engineering_radius_delta" -> SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(clamp(
                    SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get() + value,
                    2.0,
                    24.0
            ));
            case "engineering_ttl_delta" -> SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.set((int) clamp(
                    SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.get() + value,
                    10.0,
                    400.0
            ));
            case "engineering_handoff_delta" -> SwarmConfig.ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS.set(clamp(
                    SwarmConfig.ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS.get() + value,
                    0.5,
                    6.0
            ));
            case "engineering_bridge_delta" -> SwarmConfig.ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN.set((int) clamp(
                    SwarmConfig.ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN.get() + value,
                    1.0,
                    8.0
            ));
            case "engineering_baseline" -> {
                SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(true);
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS.set(2.0);
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.set(4);
                SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(8.0);
                SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.set(40);
                SwarmConfig.ZOMBIE_ENGINEERING_PATH_EVIDENCE_ENABLED.set(true);
                SwarmConfig.ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS.set(2.5);
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN.set(4);
            }

            case "nest_adaptive_toggle" ->
                    SwarmConfig.NEST_ADAPTIVE_RECRUITMENT.set(!SwarmConfig.NEST_ADAPTIVE_RECRUITMENT.get());
            case "nest_worker_share_delta" -> SwarmConfig.NEST_WORKER_TARGET_SHARE.set(clamp(
                    SwarmConfig.NEST_WORKER_TARGET_SHARE.get() + value, 0.15, 0.65
            ));
            case "nest_guard_share_delta" -> SwarmConfig.NEST_GUARD_TARGET_SHARE.set(clamp(
                    SwarmConfig.NEST_GUARD_TARGET_SHARE.get() + value, 0.10, 0.50
            ));
            case "nest_response_threshold_delta" -> SwarmConfig.NEST_RESPONSE_THRESHOLD.set(clamp(
                    SwarmConfig.NEST_RESPONSE_THRESHOLD.get() + value, 0.10, 3.0
            ));
            case "nest_lifecycle_toggle" ->
                    SwarmConfig.NEST_LIFECYCLE_ENABLED.set(!SwarmConfig.NEST_LIFECYCLE_ENABLED.get());
            case "nest_max_population_delta" -> SwarmConfig.NEST_MAX_POPULATION.set((int) clamp(
                    SwarmConfig.NEST_MAX_POPULATION.get() + value, 3.0, 32.0
            ));
            case "nest_toggle" ->
                    SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(!SwarmConfig.NEST_CONSTRUCTION_ENABLED.get());
            case "nest_interval_delta" -> SwarmConfig.NEST_BUILD_INTERVAL_TICKS.set((int) clamp(
                    SwarmConfig.NEST_BUILD_INTERVAL_TICKS.get() + value,
                    100.0, 1200.0
            ));
            case "nest_population_delta" -> SwarmConfig.NEST_MIN_GROUP_SIZE.set((int) clamp(
                    SwarmConfig.NEST_MIN_GROUP_SIZE.get() + value,
                    2.0, 16.0
            ));
            case "nest_baseline" -> {
                SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(false);
                SwarmConfig.NEST_LIFECYCLE_ENABLED.set(false);
                SwarmConfig.NEST_MAX_POPULATION.set(12);
                SwarmConfig.NEST_ADAPTIVE_RECRUITMENT.set(true);
                SwarmConfig.NEST_WORKER_TARGET_SHARE.set(0.40);
                SwarmConfig.NEST_GUARD_TARGET_SHARE.set(0.25);
                SwarmConfig.NEST_RESPONSE_THRESHOLD.set(0.55);
                SwarmConfig.NEST_BUILD_INTERVAL_TICKS.set(200);
                SwarmConfig.NEST_MIN_GROUP_SIZE.set(3);
            }

            case "ai_active_toggle" -> {
                boolean next = !SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get();
                SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.set(next);
                if (!next) {
                    SwarmAiActiveState.clear();
                }
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
            case "nav_path_budget_delta" -> SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.set((int) clamp(
                    SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.get() + value,
                    8.0,
                    512.0
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
                SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.set(96);
                SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(true);
                SwarmConfig.NAV_PATH_NODE_PENALTY.set(0.05);
                SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.set(0.25);
                SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.set(1.5);
            }

            case "baseline_all" -> {
                SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(false);
                SwarmConfig.NEST_LIFECYCLE_ENABLED.set(false);
                SwarmConfig.NEST_MAX_POPULATION.set(12);
                SwarmConfig.NEST_ADAPTIVE_RECRUITMENT.set(true);
                SwarmConfig.NEST_WORKER_TARGET_SHARE.set(0.40);
                SwarmConfig.NEST_GUARD_TARGET_SHARE.set(0.25);
                SwarmConfig.NEST_RESPONSE_THRESHOLD.set(0.55);
                SwarmConfig.NEST_BUILD_INTERVAL_TICKS.set(200);
                SwarmConfig.NEST_MIN_GROUP_SIZE.set(3);
                SwarmExperimentManager.apply(SwarmExperimentPreset.BASELINE);
                SwarmConfig.ENABLED.set(true);
                SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.set(20);
                SwarmConfig.ROLE_HYSTERESIS_TICKS.set(12);
                SwarmConfig.DIVISION_OF_LABOR_ENABLED.set(true);
                SwarmConfig.SPECIALIZATION_MIN_HOLD_TICKS.set(30);
                SwarmConfig.SPECIALIZATION_EXPERIENCE_GAIN.set(0.025);
                SwarmConfig.SPECIALIZATION_EXPERIENCE_DECAY.set(0.995);

                SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(true);
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS.set(2.0);
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.set(4);
                SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(8.0);
                SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.set(40);
                SwarmConfig.ZOMBIE_ENGINEERING_PATH_EVIDENCE_ENABLED.set(true);
                SwarmConfig.ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS.set(2.5);
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN.set(4);

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
                SwarmConfig.NAV_STUCK_WINDOW_TICKS.set(24);
                SwarmConfig.NAV_STUCK_MIN_PROGRESS.set(0.75);
                SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.set(2.0);
                SwarmConfig.NAV_RECOVERY_DURATION_TICKS.set(18);
                SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.set(1.0);
                SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.set(0.20);
                SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.set(0.75);
                SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.set(2.5);
                SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.set(96);
                SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(true);
                SwarmConfig.NAV_PATH_NODE_PENALTY.set(0.05);
                SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.set(0.25);
                SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.set(1.5);

                SwarmConfig.EXTERNAL_AI_ENABLED.set(false);
                SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.set(false);
                SwarmAiActiveState.clear();
            }

            default -> {
                // Unknown actions are ignored deliberately.
            }
        }
    }

    private static String snapshotData(
            net.minecraft.server.level.ServerLevel level,
            boolean canEdit
    ) {
        var metrics = SwarmExperimentMetrics.snapshot(level);
        var pathBudget = SwarmPathBudgetRegistry.snapshot(level);
        var navCommands = SwarmNavigationCommandTelemetry.snapshot(level);
        var colony = SwarmNestScienceTelemetry.snapshot(level);
        var colonyModel = colony.science();
        var ai = SwarmAiShadowState.snapshot();
        var decision = ai.lastDecision();
        var activeAi = SwarmAiActiveState.snapshot(level.getGameTime());
        boolean masterEnabled = SwarmConfig.ENABLED.get();
        boolean exposeDynamicAssignments =
                SwarmControlEffectiveStatePolicy.exposeDynamicAssignments(
                        masterEnabled,
                        SwarmConfig.DIVISION_OF_LABOR_ENABLED.get()
                );
        boolean effectiveActiveAi =
                SwarmControlEffectiveStatePolicy.exposeActiveAi(
                        masterEnabled,
                        SwarmConfig.EXTERNAL_AI_ENABLED.get(),
                        SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get(),
                        activeAi.active()
                );

        int zombies = 0;
        int skeletons = 0;
        int spiders = 0;
        int creepers = 0;
        int tacticalAgentsWithAllies = 0;
        int tacticalPeerLinks = 0;
        int agentsWithTacticalBreacher = 0;
        long nestsFoundedByLoadedAgents = 0L;
        EnumMap<SwarmTaskType, Integer> taskCounts = new EnumMap<>(SwarmTaskType.class);
        EnumMap<SwarmSpecialization, Integer> specializationCounts =
                new EnumMap<>(SwarmSpecialization.class);

        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof PathfinderMob mob) || !SwarmAgentProfiles.isSupported(mob)) {
                continue;
            }

            if (mob.getType() == EntityType.ZOMBIE) {
                zombies++;
            } else if (mob.getType() == EntityType.SKELETON) {
                skeletons++;
            } else if (mob.getType() == EntityType.SPIDER) {
                spiders++;
            } else if (mob.getType() == EntityType.CREEPER) {
                creepers++;
            }

            if (masterEnabled) {
                nestsFoundedByLoadedAgents +=
                        mob.getData(SwarmAttachments.AGENT_STATE.get()).nestsFounded();
            }

            if (masterEnabled) {
                SwarmAgentState tacticalState =
                        mob.getData(SwarmAttachments.AGENT_STATE.get());
                if (tacticalState.tacticalPeerCount() > 0) {
                    tacticalAgentsWithAllies++;
                    tacticalPeerLinks += tacticalState.tacticalPeerCount();
                }
                if (tacticalState.tacticalBreacherCount() > 0) {
                    agentsWithTacticalBreacher++;
                }
            }

            if (exposeDynamicAssignments) {
                SwarmAgentState state =
                        mob.getData(SwarmAttachments.AGENT_STATE.get());
                taskCounts.merge(state.currentTask(), 1, Integer::sum);
                specializationCounts.merge(
                        state.specialization(),
                        1,
                        Integer::sum
                );
            }
        }

        return String.join(";",
                pair("canEdit", canEdit),
                pair("aiEnabled", SwarmConfig.EXTERNAL_AI_ENABLED.get()),
                pair("aiModel", SwarmConfig.OLLAMA_MODEL.get()),
                pair("aiStatus", ai.status().name()),
                pair("aiInFlight", SwarmAiShadowService.requestInFlight()),
                pair("aiMode", decision.mode().name()),
                pair("aiProvider", decision.providerId()),
                pair("aiLatencyMs", (int) Math.min(Integer.MAX_VALUE, ai.lastLatencyMs())),
                pair("aiSuccessCount", (int) Math.min(Integer.MAX_VALUE, ai.successCount())),
                pair("aiFallbackCount", (int) Math.min(Integer.MAX_VALUE, ai.fallbackCount())),
                pair("aiErrorCount", (int) Math.min(Integer.MAX_VALUE, ai.errorCount())),
                pair("aiFormationMultiplier", decision.formationRadiusMultiplier()),
                pair("aiSeparationMultiplier", decision.separationMultiplier()),
                pair("aiCohesionMultiplier", decision.cohesionMultiplier()),
                pair("aiSearchRadiusMultiplier", decision.searchRadiusMultiplier()),
                pair("aiRationale", decision.rationale()),
                pair("aiLastError", ai.lastError()),
                pair("master", masterEnabled),
                pair("liveAgents", zombies + skeletons + spiders + creepers),
                pair("liveZombies", zombies),
                pair("liveSkeletons", skeletons),
                pair("liveSpiders", spiders),
                pair("liveCreepers", creepers),
                pair("nestEnabled", SwarmConfig.NEST_CONSTRUCTION_ENABLED.get()),
                pair("nestLifecycleEnabled", SwarmConfig.NEST_LIFECYCLE_ENABLED.get()),
                pair("nestMaxPopulation", SwarmConfig.NEST_MAX_POPULATION.get()),
                pair("nestAdaptiveRecruitment", SwarmConfig.NEST_ADAPTIVE_RECRUITMENT.get()),
                pair("nestWorkerShare", SwarmConfig.NEST_WORKER_TARGET_SHARE.get()),
                pair("nestGuardShare", SwarmConfig.NEST_GUARD_TARGET_SHARE.get()),
                pair("nestResponseThreshold", SwarmConfig.NEST_RESPONSE_THRESHOLD.get()),
                pair("colonyScienceAvailable", colony.available()),
                pair("colonyScienceAgeTicks", colony.available()
                        ? Math.max(0L, level.getGameTime() - colony.sampleTick()) : -1),
                pair("colonyScienceLocation", colony.available()
                        ? colony.x() + "," + colony.y() + "," + colony.z() : "unavailable"),
                pair("colonySciencePopulation", colony.population()),
                pair("colonySciencePeak", colony.peakPopulation()),
                pair("colonyScienceDelta", colony.deltaPopulation()),
                pair("colonyScienceMean", colony.averagePopulation()),
                pair("colonyScienceSamples", colony.samples()),
                pair("colonyScienceOccupancy", colonyModel.occupancy()),
                pair("colonyScienceFoodReadiness", colonyModel.nutritionReadiness()),
                pair("colonyScienceWorkers", colonyModel.workers()),
                pair("colonyScienceGuards", colonyModel.guards()),
                pair("colonyScienceScouts", colonyModel.scouts()),
                pair("colonyScienceReserves", colonyModel.reserves()),
                pair("colonyScienceNextRecruit", colonyModel.recommendedRecruit().name()),
                pair("colonyScienceWorkerResponse", colonyModel.workerResponse()),
                pair("colonyScienceGuardResponse", colonyModel.guardResponse()),
                pair("colonyScienceScoutResponse", colonyModel.scoutResponse()),
                pair("colonyScienceReserveResponse", colonyModel.reserveResponse()),
                pair("colonyScienceSoil", colony.soilPoints()),
                pair("colonyScienceTimber", colony.timberPoints()),
                pair("colonyScienceNutrient", colony.nutrientPoints()),
                pair("colonyScienceLegacy", colony.legacyPoints()),
                pair("colonyScienceTotal", colony.resourceTotal()),
                pair("colonyScienceBirths", colony.births()),
                pair("nestBuildInterval", SwarmConfig.NEST_BUILD_INTERVAL_TICKS.get()),
                pair("nestMinPopulation", SwarmConfig.NEST_MIN_GROUP_SIZE.get()),
                pair("nestCoresFoundedByLoadedAgents",
                        (int) Math.min(Integer.MAX_VALUE, nestsFoundedByLoadedAgents)),
                pair("liveTacticalAlliedAgents", tacticalAgentsWithAllies),
                pair("liveTacticalPeerLinks", tacticalPeerLinks),
                pair("liveTacticalBreacherSupport", agentsWithTacticalBreacher),
                pair("taskSearch", taskCounts.getOrDefault(SwarmTaskType.SEARCH, 0)),
                pair("taskFlank", taskCounts.getOrDefault(SwarmTaskType.FLANK, 0)),
                pair("taskBreach", taskCounts.getOrDefault(SwarmTaskType.BREACH, 0)),
                pair("taskRanged", taskCounts.getOrDefault(SwarmTaskType.RANGED_SUPPORT, 0)),
                pair("taskEngineering", taskCounts.getOrDefault(SwarmTaskType.ENGINEERING, 0)),
                pair("taskMaterial", taskCounts.getOrDefault(SwarmTaskType.MATERIAL, 0)),
                pair("taskReserve", taskCounts.getOrDefault(SwarmTaskType.RESERVE, 0)),
                pair("specEngineer", specializationCounts.getOrDefault(SwarmSpecialization.ENGINEER, 0)),
                pair("specCarrier", specializationCounts.getOrDefault(SwarmSpecialization.CARRIER, 0)),
                pair("specScout", specializationCounts.getOrDefault(SwarmSpecialization.SCOUT, 0)),
                pair("specInterceptor", specializationCounts.getOrDefault(SwarmSpecialization.INTERCEPTOR, 0)),
                pair("specOverwatch", specializationCounts.getOrDefault(SwarmSpecialization.OVERWATCH, 0)),
                pair("specLeadBreacher", specializationCounts.getOrDefault(SwarmSpecialization.LEAD_BREACHER, 0)),
                pair("divisionEnabled", SwarmConfig.DIVISION_OF_LABOR_ENABLED.get()),
                pair("specializationHoldTicks", SwarmConfig.SPECIALIZATION_MIN_HOLD_TICKS.get()),
                pair("specializationExperienceGain", SwarmConfig.SPECIALIZATION_EXPERIENCE_GAIN.get()),
                pair("specializationExperienceDecay", SwarmConfig.SPECIALIZATION_EXPERIENCE_DECAY.get()),
                pair("engineeringEnabled", SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.get()),
                pair("engineeringMaxHardness", SwarmConfig.ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS.get()),
                pair("engineeringMaxCarry", SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()),
                pair("engineeringTaskRadius", SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get()),
                pair("engineeringTaskTtl", SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.get()),
                pair("engineeringHandoffRadius", SwarmConfig.ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS.get()),
                pair("engineeringMaxBridgeSpan", SwarmConfig.ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN.get()),
                pair("metricEngineeringBroken", (int) Math.min(Integer.MAX_VALUE, metrics.engineeringBlocksBroken())),
                pair("metricEngineeringPlaced", (int) Math.min(Integer.MAX_VALUE, metrics.engineeringBlocksPlaced())),
                pair("metricEngineeringCarried", metrics.carriedEngineeringBlocks()),
                pair("metricEngineeringRequests", (int) Math.min(Integer.MAX_VALUE, metrics.engineeringRequestsPublished())),
                pair("metricEngineeringClaimed", (int) Math.min(Integer.MAX_VALUE, metrics.engineeringTasksClaimed())),
                pair("metricEngineeringCompleted", (int) Math.min(Integer.MAX_VALUE, metrics.engineeringTasksCompleted())),
                pair("aiActiveEnabled", SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get()),
                pair("aiActive", effectiveActiveAi),
                pair("aiActiveMode", effectiveActiveAi
                        ? activeAi.decision().mode().name()
                        : "BASELINE"),
                pair("aiActiveExpiresIn", effectiveActiveAi
                        ? (int) Math.max(
                                0L,
                                activeAi.expiresTick() - level.getGameTime()
                        )
                        : 0),
                "activePreset=" + SwarmExperimentManager.activePreset().name(),
                pair("experimentSeed", SwarmExperimentManager.experimentSeed()),
                pair("experimentActive", metrics.active()),
                pair("experimentElapsedTicks", (int) Math.min(Integer.MAX_VALUE, metrics.elapsedTicks())),
                pair("experimentAgents", metrics.agentCount()),
                pair("metricCommAccepted", (int) Math.min(Integer.MAX_VALUE, metrics.communicationAccepted())),
                pair("metricCommDelivered", (int) Math.min(Integer.MAX_VALUE, metrics.communicationDelivered())),
                pair("metricCommDropped", (int) Math.min(Integer.MAX_VALUE, metrics.communicationDropped())),
                pair("metricDetours", (int) Math.min(Integer.MAX_VALUE, metrics.obstacleDetours())),
                pair("metricRecoveries", (int) Math.min(Integer.MAX_VALUE, metrics.recoveries())),
                pair("metricRecoveryAttempts", (int) Math.min(Integer.MAX_VALUE, metrics.recoveryPlanningAttempts())),
                pair("metricRecoveryFailures", (int) Math.min(Integer.MAX_VALUE, metrics.recoveryPlanningFailures())),
                pair("metricPathQueries", (int) Math.min(Integer.MAX_VALUE, metrics.pathQueries())),
                pair("metricNavCommandsIssued", (int) Math.min(Integer.MAX_VALUE, navCommands.issued())),
                pair("metricNavCommandsSkipped", (int) Math.min(Integer.MAX_VALUE, navCommands.skipped())),
                pair("metricNavRetries", (int) Math.min(Integer.MAX_VALUE, navCommands.retryDone())),
                pair("metricNavRefreshes", (int) Math.min(Integer.MAX_VALUE, navCommands.periodicRefresh())),
                pair("metricRoleReassignments", (int) Math.min(Integer.MAX_VALUE, metrics.roleReassignments())),
                pair("metricSearchStarted", (int) Math.min(Integer.MAX_VALUE, metrics.searchEpisodesStarted())),
                pair("metricSearchSucceeded", (int) Math.min(Integer.MAX_VALUE, metrics.searchEpisodesSucceeded())),
                pair("metricSearchFailed", (int) Math.min(Integer.MAX_VALUE, metrics.searchEpisodesFailed())),
                pair("metricActiveSearch", metrics.activeSearchEpisodes()),
                pair("metricSearchSuccessRate", metrics.searchSuccessRate()),
                pair("metricAvgReacquisitionTicks", metrics.averageReacquisitionTicks()),
                pair("metricRecoveryFailureRate", metrics.recoveryFailureRate()),
                pair("metricObservedCommDropRate", metrics.communicationDropRate()),
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
                pair("navPathBudgetPerTick", SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.get()),
                pair("pathBudgetUsed", pathBudget.reservedTokens()),
                pair("pathBudgetWaiters", pathBudget.waiters()),
                pair("pathBudgetGranted", (int) Math.min(Integer.MAX_VALUE, pathBudget.reservationsGranted())),
                pair("pathBudgetDeferred", (int) Math.min(Integer.MAX_VALUE, pathBudget.reservationsDeferred())),
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

    private static String pair(String key, String value) {
        String safe = value == null ? "" : value
                .replace(';', ',')
                .replace('=', '~')
                .replace('\n', ' ')
                .replace('\r', ' ');
        return key + "=" + safe;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private SwarmControlNetwork() {}
}
