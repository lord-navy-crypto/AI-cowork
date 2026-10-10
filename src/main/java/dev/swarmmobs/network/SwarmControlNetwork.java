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

        // Absolute setters make Save idempotent even after reopening or packet retries.
        if (payload.action().startsWith("set:")) {
            if (!applyAbsoluteAction(payload.action().substring(4), payload.value())) {
                sendSnapshot(player);
                return;
            }
            SwarmExperimentManager.markCustom();
        } else {
            applyAction(payload.action(), payload.value(), player);
        }
        // NeoForge ConfigValue.set changes memory only; save before acknowledging.
        SwarmConfig.SPEC.save();
        sendSnapshot(player);
    }

    private static void handleSnapshot(
            ControlPanelSnapshotPayload payload,
            IPayloadContext context
    ) {
        SwarmControlClient.acceptSnapshot(payload.data());
    }

    /** Cloth Save sends desired values, not inversion commands or deltas. */
    private static boolean applyAbsoluteAction(String action, double value) {
        if (!Double.isFinite(value)) return false;
        switch (action) {
            case "toggle_master" -> { SwarmConfig.ENABLED.set(value >= 0.5); return true; }
            case "ai_toggle" -> { SwarmConfig.EXTERNAL_AI_ENABLED.set(value >= 0.5); if (value < 0.5) SwarmAiActiveState.clear(); return true; }
            case "ai_active_toggle" -> { SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.set(value >= 0.5); if (value < 0.5) SwarmAiActiveState.clear(); return true; }
            case "toggle_engineering" -> { SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(value >= 0.5); return true; }
            case "engineering_hardness_delta" -> { SwarmConfig.ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS.set(Math.max(0.0, Math.min(10.0, value))); return true; }
            case "engineering_carry_delta" -> { SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.set((int) Math.round(Math.max(0, Math.min(16, value)))); return true; }
            case "engineering_radius_delta" -> { SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(Math.max(2.0, Math.min(24.0, value))); return true; }
            case "engineering_ttl_delta" -> { SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.set((int) Math.round(Math.max(10, Math.min(400, value)))); return true; }
            case "engineering_handoff_delta" -> { SwarmConfig.ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS.set(Math.max(0.5, Math.min(6.0, value))); return true; }
            case "engineering_bridge_delta" -> { SwarmConfig.ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN.set((int) Math.round(Math.max(1, Math.min(8, value)))); return true; }
            case "nest_toggle" -> { SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(value >= 0.5); return true; }
            case "nest_visible_expansion_toggle" -> { SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.set(value >= 0.5); return true; }
            case "nest_lifecycle_toggle" -> { SwarmConfig.NEST_LIFECYCLE_ENABLED.set(value >= 0.5); return true; }
            case "nest_max_population_delta" -> { SwarmConfig.NEST_MAX_POPULATION.set((int) Math.round(Math.max(3, Math.min(32, value)))); return true; }
            case "nest_haul_toggle" -> { SwarmConfig.NEST_HAULING_ENABLED.set(value >= 0.5); return true; }
            case "nest_pheromone_toggle" -> { SwarmConfig.NEST_PHEROMONES_ENABLED.set(value >= 0.5); return true; }
            case "nest_pheromone_explore_toggle" -> { SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.set(value >= 0.5); return true; }
            case "nest_animal_hunt_toggle" -> { SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(value >= 0.5); return true; }
            case "nest_block_gather_toggle" -> { SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(value >= 0.5); return true; }
            case "nest_stock_adapt_toggle" -> { SwarmConfig.NEST_ADAPTIVE_STOCK_ENABLED.set(value >= 0.5); return true; }
            case "nest_crop_replant_toggle" -> { SwarmConfig.NEST_CROP_REPLANT_ENABLED.set(value >= 0.5); return true; }
            case "nest_gather_interval_delta" -> { SwarmConfig.NEST_GATHER_INTERVAL.set((int) Math.round(Math.max(60, Math.min(800, value)))); return true; }
            case "nest_berry_forage_toggle" -> { SwarmConfig.NEST_BERRY_FORAGING_ENABLED.set(value >= 0.5); return true; }
            case "nest_berry_forage_interval_delta" -> { SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.set((int) Math.round(Math.max(120, Math.min(800, value)))); return true; }
            case "nest_haul_radius_delta" -> { SwarmConfig.NEST_HAUL_SEARCH_RADIUS.set((int) Math.round(Math.max(4, Math.min(16, value)))); return true; }
            case "nest_haul_stack_delta" -> { SwarmConfig.NEST_HAUL_MAX_STACK.set((int) Math.round(Math.max(1, Math.min(64, value)))); return true; }
            case "nest_haul_interval_delta" -> { SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.set((int) Math.round(Math.max(40, Math.min(400, value)))); return true; }
            case "nest_adaptive_toggle" -> { SwarmConfig.NEST_ADAPTIVE_RECRUITMENT.set(value >= 0.5); return true; }
            case "nest_worker_share_delta" -> { SwarmConfig.NEST_WORKER_TARGET_SHARE.set(Math.max(0.15, Math.min(0.65, value))); return true; }
            case "nest_guard_share_delta" -> { SwarmConfig.NEST_GUARD_TARGET_SHARE.set(Math.max(0.10, Math.min(0.50, value))); return true; }
            case "nest_response_threshold_delta" -> { SwarmConfig.NEST_RESPONSE_THRESHOLD.set(Math.max(0.10, Math.min(3.0, value))); return true; }
            case "nest_interval_delta" -> { SwarmConfig.NEST_BUILD_INTERVAL_TICKS.set((int) Math.round(Math.max(100, Math.min(1200, value)))); return true; }
            case "nest_population_delta" -> { SwarmConfig.NEST_MIN_GROUP_SIZE.set((int) Math.round(Math.max(2, Math.min(16, value)))); return true; }
            case "toggle_division" -> { SwarmConfig.DIVISION_OF_LABOR_ENABLED.set(value >= 0.5); return true; }
            case "support_position_toggle" -> { SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.set(value >= 0.5); return true; }
            case "formation_hysteresis_delta" -> { SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.set((int) Math.round(Math.max(0, Math.min(200, value)))); return true; }
            case "role_hysteresis_delta" -> { SwarmConfig.ROLE_HYSTERESIS_TICKS.set((int) Math.round(Math.max(0, Math.min(200, value)))); return true; }
            case "specialization_hold_delta" -> { SwarmConfig.SPECIALIZATION_MIN_HOLD_TICKS.set((int) Math.round(Math.max(0, Math.min(400, value)))); return true; }
            case "specialization_gain_delta" -> { SwarmConfig.SPECIALIZATION_EXPERIENCE_GAIN.set(Math.max(0.0, Math.min(0.25, value))); return true; }
            case "specialization_decay_delta" -> { SwarmConfig.SPECIALIZATION_EXPERIENCE_DECAY.set(Math.max(0.90, Math.min(1.0, value))); return true; }
            case "toggle_sensing" -> { SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(value >= 0.5); return true; }
            case "sensing_drop_delta" -> { SwarmConfig.SENSING_DROPOUT_RATE.set(Math.max(0.0, Math.min(1.0, value))); return true; }
            case "sensing_noise_delta" -> { SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.set(Math.max(0.0, Math.min(8.0, value))); return true; }
            case "toggle_comm" -> { SwarmConfig.COMMUNICATION_ENABLED.set(value >= 0.5); return true; }
            case "comm_drop_delta" -> { SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(Math.max(0.0, Math.min(1.0, value))); return true; }
            case "comm_latency_delta" -> { SwarmConfig.COMMUNICATION_LATENCY_TICKS.set((int) Math.round(Math.max(0, Math.min(400, value)))); return true; }
            case "comm_radius_delta" -> { SwarmConfig.COMMUNICATION_RADIUS.set(Math.max(1.0, Math.min(96.0, value))); return true; }
            case "search_threshold_delta" -> { SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.set(Math.max(0.05, Math.min(0.95, value))); return true; }
            case "search_speed_delta" -> { SwarmConfig.SEARCH_SPEED_FACTOR.set(Math.max(0.25, Math.min(1.5, value))); return true; }
            case "search_max_radius_delta" -> { SwarmConfig.SEARCH_MAX_RADIUS.set(Math.max(2.0, Math.min(32.0, value))); return true; }
            case "toggle_prediction" -> { SwarmConfig.TARGET_PREDICTION_ENABLED.set(value >= 0.5); return true; }
            case "prediction_distance_delta" -> { SwarmConfig.TARGET_PREDICTION_MAX_DISTANCE.set(Math.max(0.0, Math.min(12.0, value))); return true; }
            case "toggle_obstacle" -> { SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(value >= 0.5); return true; }
            case "nav_lookahead_delta" -> { SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.set(Math.max(0.5, Math.min(4.0, value))); return true; }
            case "nav_lateral_delta" -> { SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.set(Math.max(0.5, Math.min(4.0, value))); return true; }
            case "toggle_walkability" -> { SwarmConfig.NAV_WALKABILITY_ENABLED.set(value >= 0.5); return true; }
            case "nav_max_drop_delta" -> { SwarmConfig.NAV_MAX_PROBE_DROP_BLOCKS.set((int) Math.round(Math.max(0, Math.min(4, value)))); return true; }
            case "nav_stuck_window_delta" -> { SwarmConfig.NAV_STUCK_WINDOW_TICKS.set((int) Math.round(Math.max(6, Math.min(200, value)))); return true; }
            case "nav_stuck_progress_delta" -> { SwarmConfig.NAV_STUCK_MIN_PROGRESS.set(Math.max(0.05, Math.min(4.0, value))); return true; }
            case "nav_recovery_distance_delta" -> { SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.set(Math.max(0.25, Math.min(6.0, value))); return true; }
            case "nav_recovery_duration_delta" -> { SwarmConfig.NAV_RECOVERY_DURATION_TICKS.set((int) Math.round(Math.max(3, Math.min(100, value)))); return true; }
            case "nav_progress_weight_delta" -> { SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.set(Math.max(0.0, Math.min(4.0, value))); return true; }
            case "nav_lateral_penalty_delta" -> { SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.set(Math.max(0.0, Math.min(4.0, value))); return true; }
            case "nav_congestion_penalty_delta" -> { SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.set(Math.max(0.0, Math.min(4.0, value))); return true; }
            case "nav_congestion_radius_delta" -> { SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.set(Math.max(0.5, Math.min(8.0, value))); return true; }
            case "toggle_path_evidence" -> { SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(value >= 0.5); return true; }
            case "nav_path_budget_delta" -> { SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.set((int) Math.round(Math.max(8, Math.min(512, value)))); return true; }
            case "nav_path_node_penalty_delta" -> { SwarmConfig.NAV_PATH_NODE_PENALTY.set(Math.max(0.0, Math.min(2.0, value))); return true; }
            case "nav_path_residual_penalty_delta" -> { SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.set(Math.max(0.0, Math.min(2.0, value))); return true; }
            case "nav_path_max_residual_delta" -> { SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.set(Math.max(0.0, Math.min(4.0, value))); return true; }
            case "experiment_seed_delta" -> { SwarmExperimentManager.setExperimentSeed((int) Math.round(Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)))); return true; }
            default -> { return false; }
        }
    }

    private static void applyAction(String action, double value, ServerPlayer player) {
        if (!action.startsWith("preset_")
                && !action.startsWith("ai_")
                && !action.equals("experiment_seed_delta")) {
            SwarmExperimentManager.markCustom();
        }

        switch (action) {
            case "playtest_enable", "playtest_disable" -> {
                boolean enabled = action.equals("playtest_enable");
                SwarmConfig.ENABLED.set(true);
                SwarmConfig.COMMUNICATION_ENABLED.set(true);
                SwarmConfig.DIVISION_OF_LABOR_ENABLED.set(true);
                SwarmConfig.TARGET_PREDICTION_ENABLED.set(true);
                SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.set(true);
                SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(true);
                SwarmConfig.NAV_WALKABILITY_ENABLED.set(true);
                SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(true);
                SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(true);
                SwarmConfig.ZOMBIE_ENGINEERING_PATH_EVIDENCE_ENABLED.set(true);
                SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(enabled);
                SwarmConfig.NEST_LIFECYCLE_ENABLED.set(enabled);
                SwarmConfig.NEST_HAULING_ENABLED.set(enabled);
                SwarmConfig.NEST_BERRY_FORAGING_ENABLED.set(enabled);
                SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(enabled);
                SwarmConfig.NEST_CROP_REPLANT_ENABLED.set(enabled);
                SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(enabled);
                SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.set(enabled);
                SwarmConfig.NEST_ADAPTIVE_STOCK_ENABLED.set(true);
                SwarmConfig.NEST_ADAPTIVE_RECRUITMENT.set(true);
                SwarmConfig.NEST_PHEROMONES_ENABLED.set(true);
                SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.set(true);
                if (enabled) {
                    SwarmConfig.NEST_BUILD_INTERVAL_TICKS.set(100);
                    SwarmConfig.NEST_GATHER_INTERVAL.set(60);
                    SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.set(40);
                    SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.set(120);
                    SwarmConfig.NEST_HAUL_MAX_STACK.set(64);
                    player.serverLevel().getGameRules().getRule(
                            net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                            .set(true, player.getServer());
                    player.serverLevel().getGameRules().getRule(
                            net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                            .set(true, player.getServer());
                }
            }
            case "toggle_master" -> SwarmConfig.ENABLED.set(!SwarmConfig.ENABLED.get());
            case "support_position_toggle" -> SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.set(
                    !SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.get());

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
                SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.set(false);
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
            case "nest_visible_expansion_toggle" ->
                    SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.set(
                            !SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.get());
            case "nest_haul_toggle" ->
                    SwarmConfig.NEST_HAULING_ENABLED.set(!SwarmConfig.NEST_HAULING_ENABLED.get());
            case "nest_berry_forage_toggle" ->
                    SwarmConfig.NEST_BERRY_FORAGING_ENABLED.set(
                            !SwarmConfig.NEST_BERRY_FORAGING_ENABLED.get());
            case "nest_block_gather_toggle" ->
                    SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(
                            !SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get());
            case "nest_crop_replant_toggle" ->
                    SwarmConfig.NEST_CROP_REPLANT_ENABLED.set(
                            !SwarmConfig.NEST_CROP_REPLANT_ENABLED.get());
            case "nest_stock_adapt_toggle" ->
                    SwarmConfig.NEST_ADAPTIVE_STOCK_ENABLED.set(
                            !SwarmConfig.NEST_ADAPTIVE_STOCK_ENABLED.get());
            case "nest_animal_hunt_toggle" ->
                    SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(
                            !SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.get());
            case "nest_pheromone_toggle" ->
                    SwarmConfig.NEST_PHEROMONES_ENABLED.set(
                            !SwarmConfig.NEST_PHEROMONES_ENABLED.get());
            case "nest_pheromone_explore_toggle" ->
                    SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.set(
                            !SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.get());
            case "nest_gather_interval_delta" ->
                    SwarmConfig.NEST_GATHER_INTERVAL.set((int) clamp(
                            SwarmConfig.NEST_GATHER_INTERVAL.get() + value,
                            60.0, 800.0));
            case "nest_berry_forage_interval_delta" ->
                    SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.set((int) clamp(
                            SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.get() + value, 120.0, 800.0));
            case "nest_haul_radius_delta" -> SwarmConfig.NEST_HAUL_SEARCH_RADIUS.set((int) clamp(
                    SwarmConfig.NEST_HAUL_SEARCH_RADIUS.get() + value, 4.0, 16.0));
            case "nest_haul_stack_delta" -> SwarmConfig.NEST_HAUL_MAX_STACK.set((int) clamp(
                    SwarmConfig.NEST_HAUL_MAX_STACK.get() + value, 1.0, 64.0));
            case "nest_haul_interval_delta" -> SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.set((int) clamp(
                    SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.get() + value, 40.0, 400.0));
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
                SwarmConfig.NEST_HAULING_ENABLED.set(false);
                SwarmConfig.NEST_BERRY_FORAGING_ENABLED.set(false);
                SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.set(200);
                SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(false);
                SwarmConfig.NEST_CROP_REPLANT_ENABLED.set(false);
                SwarmConfig.NEST_ADAPTIVE_STOCK_ENABLED.set(true);
                SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(false);
                SwarmConfig.NEST_GATHER_INTERVAL.set(160);
                SwarmConfig.NEST_PHEROMONES_ENABLED.set(true);
                SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.set(true);
                SwarmConfig.NEST_HAUL_SEARCH_RADIUS.set(8);
                SwarmConfig.NEST_HAUL_MAX_STACK.set(16);
                SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.set(100);
                SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.set(false);
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
                SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.set(false);
                SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(false);
                SwarmConfig.NEST_LIFECYCLE_ENABLED.set(false);
                SwarmConfig.NEST_HAULING_ENABLED.set(false);
                SwarmConfig.NEST_BERRY_FORAGING_ENABLED.set(false);
                SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.set(200);
                SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(false);
                SwarmConfig.NEST_CROP_REPLANT_ENABLED.set(false);
                SwarmConfig.NEST_ADAPTIVE_STOCK_ENABLED.set(true);
                SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(false);
                SwarmConfig.NEST_GATHER_INTERVAL.set(160);
                SwarmConfig.NEST_PHEROMONES_ENABLED.set(true);
                SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.set(true);
                SwarmConfig.NEST_HAUL_SEARCH_RADIUS.set(8);
                SwarmConfig.NEST_HAUL_MAX_STACK.set(16);
                SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.set(100);
                SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.set(false);
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
        int workAgents = 0, alertAgents = 0, combatAgents = 0, recoveringAgents = 0;
        int tacticSweep = 0, tacticSurround = 0, tacticStandard = 0, tacticSearch = 0;
        int activeFlankFillers = 0;
        int skeletonSpacing = 0;
        long skeletonSpacingEpisodes = 0;
        long skeletonMoveFallbacks = 0;
        long zombieFlankFallbacks = 0;
        long skeletonNoProgress = 0;
        long zombieNoProgress = 0;
        int skeletonFailedSites = 0;
        int zombieFailedSites = 0;
        long flankFillEpisodes = 0;
        int searchRallying = 0;
        long searchRallyEpisodes = 0;
        int laneDiverted = 0;
        long laneDiversionSamples = 0;
        long laneBlockedFallbacks = 0;
        long staleRouteResets = 0;
        int optimizedSupportAgents = 0;
        long supportLaneSwitches = 0, supportFeasibleSamples = 0,
                supportUnavailableSamples = 0;
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
                if (tacticalState.targetId() != null) {
                    if (tacticalState.coveringVacantFlank()) activeFlankFillers++;
                    if (tacticalState.rangedSpacingActive()) skeletonSpacing++;
                    skeletonSpacingEpisodes += tacticalState.rangedSpacingEpisodes();
                    skeletonMoveFallbacks += tacticalState.rangedSpacingFallbacks();
                    zombieFlankFallbacks += tacticalState.zombieFlankFallbacks();
                    skeletonNoProgress += tacticalState.rangedNoProgressFallbacks();
                    zombieNoProgress += tacticalState.zombieNoProgressFallbacks();
                    skeletonFailedSites += tacticalState.recentRangedFailureLocations(level.getGameTime());
                    zombieFailedSites += tacticalState.recentZombieFailureLocations(level.getGameTime());
                    flankFillEpisodes += tacticalState.vacantFlankCoverageEpisodes();
                    if (tacticalState.searchRallyActive()) searchRallying++;
                    searchRallyEpisodes += tacticalState.searchRallyEpisodes();
                    if (tacticalState.crowdLaneSide() != 0) laneDiverted++;
                    laneDiversionSamples += tacticalState.crowdLaneUses();
                    laneBlockedFallbacks += tacticalState.crowdLaneRejected();
                    staleRouteResets += tacticalState.navigationEpisodeResets();
                    switch (tacticalState.tacticalPattern()) {
                        case "SWEEP" -> tacticSweep++;
                        case "SURROUND" -> tacticSurround++;
                        case "SEARCH" -> tacticSearch++;
                        default -> tacticStandard++;
                    }
                }
                if (SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.get()
                        && tacticalState.engagementMode()
                                == dev.swarmmobs.algorithm.SwarmEngagementPolicy.Mode.COMBAT
                        && tacticalState.tacticalPeerCount() > 0) {
                    if (tacticalState.supportPositionSide() != 0) optimizedSupportAgents++;
                    supportLaneSwitches += tacticalState.supportPositionSwitches();
                    supportFeasibleSamples += tacticalState.supportPositionFeasibleSamples();
                    supportUnavailableSamples += tacticalState.supportPositionUnavailableSamples();
                }
                switch (tacticalState.engagementMode()) {
                    case WORK -> workAgents++;
                    case ALERT -> alertAgents++;
                    case COMBAT -> combatAgents++;
                    case RECOVERY -> recoveringAgents++;
                }
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
                pair("staleRouteResets", Math.min(Integer.MAX_VALUE, staleRouteResets)),
                pair("laneBlockedFallbacks", Math.min(Integer.MAX_VALUE, laneBlockedFallbacks)),
                pair("laneDiverted", laneDiverted),
                pair("laneDiversionSamples", Math.min(Integer.MAX_VALUE, laneDiversionSamples)),
                pair("skeletonSpacing", skeletonSpacing),
                pair("skeletonSpacingEpisodes", Math.min(Integer.MAX_VALUE, skeletonSpacingEpisodes)),
                pair("skeletonMoveFallbacks", Math.min(Integer.MAX_VALUE, skeletonMoveFallbacks)),
                pair("zombieFlankFallbacks", Math.min(Integer.MAX_VALUE, zombieFlankFallbacks)),
                pair("skeletonNoProgress", Math.min(Integer.MAX_VALUE, skeletonNoProgress)),
                pair("zombieNoProgress", Math.min(Integer.MAX_VALUE, zombieNoProgress)),
                pair("skeletonFailedSites", skeletonFailedSites),
                pair("zombieFailedSites", zombieFailedSites),
                pair("activeFlankFillers", activeFlankFillers),
                pair("flankFillEpisodes", Math.min(Integer.MAX_VALUE, flankFillEpisodes)),
                pair("tacticSweep", tacticSweep),
                pair("tacticSurround", tacticSurround),
                pair("tacticStandard", tacticStandard),
                pair("tacticSearch", tacticSearch),
                pair("searchRallying", searchRallying),
                pair("searchRallyEpisodes", Math.min(Integer.MAX_VALUE, searchRallyEpisodes)),
                pair("optimizedSupportAgents", optimizedSupportAgents),
                pair("supportLaneSwitches", Math.min(Integer.MAX_VALUE,supportLaneSwitches)),
                pair("supportFeasibleSamples", Math.min(Integer.MAX_VALUE,supportFeasibleSamples)),
                pair("supportUnavailableSamples", Math.min(Integer.MAX_VALUE,supportUnavailableSamples)),
                pair("modeWorkAgents", workAgents),
                pair("modeAlertAgents", alertAgents),
                pair("modeCombatAgents", combatAgents),
                pair("modeRecoveryAgents", recoveringAgents),
                pair("liveZombies", zombies),
                pair("liveSkeletons", skeletons),
                pair("liveSpiders", spiders),
                pair("liveCreepers", creepers),
                pair("nestEnabled", SwarmConfig.NEST_CONSTRUCTION_ENABLED.get()),
                pair("nestLifecycleEnabled", SwarmConfig.NEST_LIFECYCLE_ENABLED.get()),
                pair("nestHaulingEnabled", SwarmConfig.NEST_HAULING_ENABLED.get()),
                pair("nestBerryForagingEnabled", SwarmConfig.NEST_BERRY_FORAGING_ENABLED.get()),
                pair("nestBlockGatherEnabled", SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get()),
                pair("nestCropReplantEnabled", SwarmConfig.NEST_CROP_REPLANT_ENABLED.get()),
                pair("nestAdaptiveStockEnabled", SwarmConfig.NEST_ADAPTIVE_STOCK_ENABLED.get()),
                pair("nestAnimalHuntEnabled", SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.get()),
                pair("nestGatherInterval", SwarmConfig.NEST_GATHER_INTERVAL.get()),
                pair("nestPheromonesEnabled", SwarmConfig.NEST_PHEROMONES_ENABLED.get()),
                pair("nestPheromoneExplorationEnabled",
                        SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.get()),
                pair("nestBerryForageInterval", SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.get()),
                pair("nestHaulSearchRadius", SwarmConfig.NEST_HAUL_SEARCH_RADIUS.get()),
                pair("nestHaulMaxStack", SwarmConfig.NEST_HAUL_MAX_STACK.get()),
                pair("nestHaulAttemptInterval", SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.get()),
                pair("colonyHaulItems", colony.hauledItems()),
                pair("colonyHaulTrips", colony.haulTrips()),
                pair("colonyForagedBerries", colony.foragedBerries()),
                pair("colonyActiveWorkSites", colony.activeWorkSites()),
                pair("colonyScoutItemLeads", colony.scoutItemLeads()),
                pair("colonyOpportunityLeads", colony.activeOpportunities()),
                pair("colonyOpportunityWorkers", colony.opportunityWorkers()),
                pair("colonyOpportunityReports", colony.opportunityReports()),
                pair("colonyOpportunityInvalidations", colony.opportunityInvalidations()),
                pair("colonyReinforcedTrips", colony.reinforcedTrips()),
                pair("colonyInhibitedJobs", colony.inhibitedJobs()),
                pair("colonyFoodRecruitment", colony.foodRecruitment()),
                pair("colonyTimberRecruitment", colony.timberRecruitment()),
                pair("colonySoilRecruitment", colony.soilRecruitment()),
                pair("colonyFoodInhibition", colony.foodInhibition()),
                pair("colonyTimberInhibition", colony.timberInhibition()),
                pair("colonySoilInhibition", colony.soilInhibition()),
                pair("colonyTargetSoil", colony.targetSoil()),
                pair("colonyTargetTimber", colony.targetTimber()),
                pair("colonyTargetFood", colony.targetFood()),
                pair("colonyOwnedShellPieces", colony.ownedShellPieces()),
                pair("colonyPheromoneCells", colony.pheromoneCells()),
                pair("colonyPheromoneObserved", colony.pheromoneObservations()),
                pair("colonyPheromoneReinforced", colony.pheromoneReinforcements()),
                pair("colonyPheromoneStopped", colony.pheromoneStopSignals()),
                pair("nestVisibleExpansionEnabled",
                        SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.get()),
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
                pair("colonyScienceChamberLevel", colony.chamberLevel()),
                pair("colonyScienceVisibleShellLevel", colony.visibleChamberLevel()),
                pair("colonyScienceCapacity", colony.colonyCapacity()),
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
                pair("supportPositionOptimizationEnabled",
                        SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.get()),
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
