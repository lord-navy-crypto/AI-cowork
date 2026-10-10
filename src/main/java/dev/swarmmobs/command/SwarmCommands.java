package dev.swarmmobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmPlannerContext;
import dev.swarmmobs.ai.SwarmAiActiveState;
import dev.swarmmobs.ai.SwarmAiShadowService;
import dev.swarmmobs.ai.SwarmAiShadowState;
import dev.swarmmobs.ai.ollama.OllamaStrategyProvider;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.colony.SwarmNestArchitecturePolicy;
import dev.swarmmobs.registry.SwarmNestBlocks;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.debug.SwarmDebugState;
import dev.swarmmobs.network.SwarmControlNetwork;
import dev.swarmmobs.experiment.SwarmExperimentManager;
import dev.swarmmobs.experiment.SwarmExperimentMetrics;
import dev.swarmmobs.experiment.SwarmExperimentPreset;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;

public final class SwarmCommands {

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("swarmmobs")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("inspect")
                        .executes(context -> inspectNearest(context.getSource())))
                .then(Commands.literal("group")
                        .executes(context -> inspectGroup(context.getSource())))
                .then(Commands.literal("panel")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> openControlPanel(context.getSource())));

        var debug = Commands.literal("debug")
                .requires(source -> source.hasPermission(2));

        debug.then(
                Commands.literal("spawn")
                        .then(Commands.argument("count", IntegerArgumentType.integer(2, 32))
                                .executes(context -> spawnTestSwarm(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")
                                )))
        );

        debug.then(
                Commands.literal("spawnmixed")
                        .then(Commands.argument("count", IntegerArgumentType.integer(2, 32))
                                .executes(context -> spawnMixedSwarm(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")
                                )))
        );

        var particles = Commands.literal("particles")
                .then(Commands.literal("toggle")
                        .executes(context -> toggleParticles(context.getSource())))
                .then(Commands.literal("on")
                        .executes(context -> setParticles(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setParticles(context.getSource(), false)));

        debug.then(particles);

        // Operator-only activation for existing worlds with persisted OFF server configs.
        debug.then(Commands.literal("testmode")
                .then(Commands.literal("on")
                        .executes(context -> setPlaytestMode(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setPlaytestMode(context.getSource(), false)))
                .then(Commands.literal("status")
                        .executes(context -> playtestModeStatus(context.getSource()))));
        debug.then(Commands.literal("workstatus")
                .executes(context -> nearestWorkerStatus(context.getSource())));
        debug.then(Commands.literal("neststatus")
                .executes(context -> inspectLookedAtNest(context.getSource())));

        var communication = Commands.literal("comm")
                .then(Commands.literal("on")
                        .executes(context -> setCommunicationEnabled(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setCommunicationEnabled(context.getSource(), false)))
                .then(Commands.literal("baseline")
                        .executes(context -> setCommunicationBaseline(context.getSource())))
                .then(Commands.literal("latency")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 400))
                                .executes(context -> setCommunicationLatency(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "ticks")
                                ))))
                .then(Commands.literal("drop")
                        .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes(context -> setCommunicationDropRate(
                                        context.getSource(),
                                        DoubleArgumentType.getDouble(context, "rate")
                                ))))
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1.0, 96.0))
                                .executes(context -> setCommunicationRadius(
                                        context.getSource(),
                                        DoubleArgumentType.getDouble(context, "blocks")
                                ))))
                .then(Commands.literal("seed")
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(context -> setCommunicationSeed(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "value")
                                ))));

        debug.then(communication);

        var sensing = Commands.literal("sensing")
                .then(Commands.literal("on")
                        .executes(context -> setSensingEnabled(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setSensingEnabled(context.getSource(), false)))
                .then(Commands.literal("baseline")
                        .executes(context -> setSensingBaseline(context.getSource())))
                .then(Commands.literal("drop")
                        .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes(context -> setSensingDropoutRate(
                                        context.getSource(),
                                        DoubleArgumentType.getDouble(context, "rate")
                                ))))
                .then(Commands.literal("noise")
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0, 8.0))
                                .executes(context -> setSensingNoise(
                                        context.getSource(),
                                        DoubleArgumentType.getDouble(context, "blocks")
                                ))))
                .then(Commands.literal("seed")
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(context -> setSensingSeed(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "value")
                                ))));

        debug.then(sensing);

        var experiment = Commands.literal("experiment")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("baseline")
                        .executes(context -> applyExperimentPreset(context.getSource(), SwarmExperimentPreset.BASELINE)))
                .then(Commands.literal("noisy_sensing")
                        .executes(context -> applyExperimentPreset(context.getSource(), SwarmExperimentPreset.NOISY_SENSING)))
                .then(Commands.literal("lossy_comms")
                        .executes(context -> applyExperimentPreset(context.getSource(), SwarmExperimentPreset.LOSSY_COMMS)))
                .then(Commands.literal("combined_faults")
                        .executes(context -> applyExperimentPreset(context.getSource(), SwarmExperimentPreset.COMBINED_FAULTS)))
                .then(Commands.literal("navigation_stress")
                        .executes(context -> applyExperimentPreset(context.getSource(), SwarmExperimentPreset.NAVIGATION_STRESS)))
                .then(Commands.literal("seed")
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(context -> setExperimentSeed(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "value")
                                ))))
                .then(Commands.literal("start")
                        .executes(context -> startExperiment(context.getSource())))
                .then(Commands.literal("reset")
                        .executes(context -> resetExperiment(context.getSource())))
                .then(Commands.literal("snapshot")
                        .executes(context -> snapshotExperiment(context.getSource())));

        var ai = Commands.literal("ai")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(context -> aiStatus(context.getSource())))
                .then(Commands.literal("models")
                        .executes(context -> aiModels(context.getSource())))
                .then(Commands.literal("model")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> aiModel(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))
                .then(Commands.literal("on")
                        .executes(context -> setAiEnabled(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setAiEnabled(context.getSource(), false)))
                .then(Commands.literal("shadow")
                        .executes(context -> runAiShadow(context.getSource())))
                .then(Commands.literal("active")
                        .then(Commands.literal("on")
                                .executes(context -> setAiActiveEnabled(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setAiActiveEnabled(context.getSource(), false)))
                        .then(Commands.literal("status")
                                .executes(context -> aiActiveStatus(context.getSource())))
                        .then(Commands.literal("apply")
                                .executes(context -> runAiActive(context.getSource()))));

        root.then(experiment);
        root.then(ai);
        root.then(debug);
        dispatcher.register(root);
    }

    private static int setAiEnabled(CommandSourceStack source, boolean enabled) {
        SwarmConfig.EXTERNAL_AI_ENABLED.set(enabled);
        if (!enabled) {
            SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.set(false);
            SwarmAiActiveState.clear();
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm AI provider: " + (enabled ? "ON" : "OFF")
                                + (enabled
                                ? ". Shadow requests are available; active strategy remains separately opt-in."
                                : ". Active strategy cleared; deterministic gameplay is fully restored.")
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setAiActiveEnabled(CommandSourceStack source, boolean enabled) {
        SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.set(enabled);
        if (enabled) {
            SwarmConfig.EXTERNAL_AI_ENABLED.set(true);
        } else {
            SwarmAiActiveState.clear();
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Swarm AI active strategy: " + (enabled ? "ON" : "OFF")
                                + (enabled
                                ? ". Use /swarmmobs ai active apply to request one bounded strategy."
                                : ". Overlay cleared; deterministic multipliers restored.")
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int aiActiveStatus(CommandSourceStack source) {
        var active = SwarmAiActiveState.snapshot(source.getLevel().getGameTime());
        source.sendSuccess(
                () -> Component.literal(String.format(
                        java.util.Locale.ROOT,
                        "AI active: enabled=%s, active=%s, mode=%s, provider=%s, formation=%.2f, separation=%.2f, cohesion=%.2f, searchRadius=%.2f, expiresTick=%d, pendingMode=%s, pendingEligibleTick=%d",
                        SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get(),
                        active.active(),
                        active.decision().mode(),
                        active.decision().providerId(),
                        active.formationRadiusMultiplier(),
                        active.separationMultiplier(),
                        active.cohesionMultiplier(),
                        active.searchRadiusMultiplier(),
                        active.expiresTick(),
                        active.pendingDecision() == null ? "none" : active.pendingDecision().mode(),
                        active.pendingEligibleTick()
                )),
                false
        );
        return 1;
    }

    private static int runAiActive(CommandSourceStack source) {
        if (!SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get()) {
            source.sendFailure(Component.literal(
                    "Active AI strategy is OFF. Run /swarmmobs ai active on first."
            ));
            return 0;
        }
        if (SwarmAiShadowService.requestInFlight()) {
            source.sendFailure(Component.literal("An AI strategy request is already in flight."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Active AI request started. Only sanitized high-level multipliers/ASSAULT role bias may be applied."
                ),
                false
        );

        SwarmAiShadowService.requestActive(source.getLevel()).whenComplete((decision, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal(
                                "Active AI request failed; current/baseline deterministic strategy remains valid: "
                                        + error.getMessage()
                        ));
                    } else {
                        source.sendSuccess(
                                () -> Component.literal(String.format(
                                        java.util.Locale.ROOT,
                                        "Active AI candidate: mode=%s, formation=%.2f, separation=%.2f, cohesion=%.2f, searchRadius=%.2f, provider=%s. TTL=%d ticks, minHold=%d ticks.",
                                        decision.mode(),
                                        decision.formationRadiusMultiplier(),
                                        decision.separationMultiplier(),
                                        decision.cohesionMultiplier(),
                                        decision.searchRadiusMultiplier(),
                                        decision.providerId(),
                                        SwarmConfig.EXTERNAL_AI_ACTIVE_TTL_TICKS.get(),
                                        SwarmConfig.EXTERNAL_AI_ACTIVE_MIN_HOLD_TICKS.get()
                                )),
                                false
                        );
                    }
                })
        );
        return 1;
    }

    private static int aiModel(CommandSourceStack source, String model) {
        String selected = model == null ? "" : model.trim();
        SwarmConfig.OLLAMA_MODEL.set(selected);
        source.sendSuccess(
                () -> Component.literal(
                        selected.isBlank()
                                ? "Swarm AI model cleared."
                                : "Swarm AI model=" + selected
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int aiStatus(CommandSourceStack source) {
        var shadow = SwarmAiShadowState.snapshot();
        var active = SwarmAiActiveState.snapshot(source.getLevel().getGameTime());
        source.sendSuccess(
                () -> Component.literal(
                        "AI shadow: enabled=" + SwarmConfig.EXTERNAL_AI_ENABLED.get()
                                + ", model=" + SwarmConfig.OLLAMA_MODEL.get()
                                + ", endpoint=" + SwarmConfig.OLLAMA_BASE_URL.get()
                                + ", requestInFlight=" + SwarmAiShadowService.requestInFlight()
                                + ", state=" + shadow.status()
                                + ", lastMode=" + shadow.lastDecision().mode()
                                + ", provider=" + shadow.lastDecision().providerId()
                                + ", latencyMs=" + shadow.lastLatencyMs()
                                + ", success=" + shadow.successCount()
                                + ", fallback=" + shadow.fallbackCount()
                                + ", errors=" + shadow.errorCount()
                                + ", rationale=" + shadow.lastDecision().rationale()
                                + ", activeEnabled=" + SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get()
                                + ", active=" + active.active()
                                + ", activeMode=" + active.decision().mode()
                                + ", activeExpiresTick=" + active.expiresTick()
                ),
                false
        );

        OllamaStrategyProvider.INSTANCE.status().whenComplete((status, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Ollama status failed: " + error.getMessage()));
                    } else {
                        source.sendSuccess(
                                () -> Component.literal(
                                        "Ollama: available=" + status.available()
                                                + ", model=" + status.model()
                                                + ", " + status.message()
                                ),
                                false
                        );
                    }
                })
        );
        return 1;
    }

    private static int aiModels(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Querying local Ollama models asynchronously..."), false);
        OllamaStrategyProvider.INSTANCE.listModels().whenComplete((models, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Ollama model query failed: " + error.getMessage()));
                    } else {
                        source.sendSuccess(
                                () -> Component.literal(
                                        models.isEmpty()
                                                ? "Ollama reported no local models."
                                                : "Ollama models: " + String.join(", ", models)
                                ),
                                false
                        );
                    }
                })
        );
        return 1;
    }

    private static int runAiShadow(CommandSourceStack source) {
        if (SwarmAiShadowService.requestInFlight()) {
            source.sendFailure(Component.literal("A shadow AI request is already in flight."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "AI shadow request started. Recommendation will be recorded only; gameplay is unchanged."
                ),
                false
        );

        SwarmAiShadowService.request(source.getLevel()).whenComplete((decision, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("AI shadow request failed: " + error.getMessage()));
                    } else {
                        source.sendSuccess(
                                () -> Component.literal(String.format(
                                        java.util.Locale.ROOT,
                                        "AI shadow recommendation: mode=%s, formation=%.2f, separation=%.2f, cohesion=%.2f, searchRadius=%.2f, provider=%s, rationale=%s",
                                        decision.mode(),
                                        decision.formationRadiusMultiplier(),
                                        decision.separationMultiplier(),
                                        decision.cohesionMultiplier(),
                                        decision.searchRadiusMultiplier(),
                                        decision.providerId(),
                                        decision.rationale()
                                )),
                                false
                        );
                    }
                })
        );
        return 1;
    }

    private static int applyExperimentPreset(CommandSourceStack source, SwarmExperimentPreset preset) {
        SwarmExperimentManager.apply(preset);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm experiment preset=" + preset
                                + ", seed=" + SwarmExperimentManager.experimentSeed()
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setExperimentSeed(CommandSourceStack source, int seed) {
        SwarmExperimentManager.setExperimentSeed(seed);
        source.sendSuccess(
                () -> Component.literal("Swarm experiment seed=" + seed),
                true
        );
        return 1;
    }

    private static int startExperiment(CommandSourceStack source) {
        SwarmExperimentMetrics.start(source.getLevel());
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm experiment run started: preset=" + SwarmExperimentManager.activePreset()
                                + ", seed=" + SwarmExperimentManager.experimentSeed()
                ),
                true
        );
        return 1;
    }

    private static int resetExperiment(CommandSourceStack source) {
        SwarmExperimentMetrics.reset(source.getLevel());
        source.sendSuccess(() -> Component.literal("Swarm experiment measurement baseline reset."), true);
        return 1;
    }

    private static int snapshotExperiment(CommandSourceStack source) {
        var metrics = SwarmExperimentMetrics.snapshot(source.getLevel());
        source.sendSuccess(
                () -> Component.literal(String.format(
                        java.util.Locale.ROOT,
                        "Experiment snapshot: active=%s, elapsedTicks=%d, agents=%d, commAccepted=%d, commDelivered=%d, commDropped=%d, observedCommDropRate=%.3f, detours=%d, recoveries=%d, recoveryAttempts=%d, recoveryFailures=%d, recoveryFailureRate=%.3f, pathQueries=%d, roleReassignments=%d, searchStarted=%d, searchSucceeded=%d, searchFailed=%d, activeSearch=%d, searchSuccessRate=%.3f, avgReacquisitionTicks=%.2f, engineeringBroken=%d, engineeringPlaced=%d, carriedEngineeringBlocks=%d, engineeringRequests=%d, engineeringClaims=%d, engineeringCompleted=%d, engineeringMaterialsGiven=%d, engineeringMaterialsReceived=%d",
                        metrics.active(),
                        metrics.elapsedTicks(),
                        metrics.agentCount(),
                        metrics.communicationAccepted(),
                        metrics.communicationDelivered(),
                        metrics.communicationDropped(),
                        metrics.communicationDropRate(),
                        metrics.obstacleDetours(),
                        metrics.recoveries(),
                        metrics.recoveryPlanningAttempts(),
                        metrics.recoveryPlanningFailures(),
                        metrics.recoveryFailureRate(),
                        metrics.pathQueries(),
                        metrics.roleReassignments(),
                        metrics.searchEpisodesStarted(),
                        metrics.searchEpisodesSucceeded(),
                        metrics.searchEpisodesFailed(),
                        metrics.activeSearchEpisodes(),
                        metrics.searchSuccessRate(),
                        metrics.averageReacquisitionTicks(),
                        metrics.engineeringBlocksBroken(),
                        metrics.engineeringBlocksPlaced(),
                        metrics.carriedEngineeringBlocks(),
                        metrics.engineeringRequestsPublished(),
                        metrics.engineeringTasksClaimed(),
                        metrics.engineeringTasksCompleted(),
                        metrics.engineeringMaterialsGiven(),
                        metrics.engineeringMaterialsReceived()
                )),
                false
        );
        return 1;
    }

    private static int openControlPanel(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }

        SwarmControlNetwork.sendSnapshot(player);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm Mobs: preset=" + SwarmExperimentManager.activePreset()
                                + ", experimentSeed=" + SwarmExperimentManager.experimentSeed()
                                + ", enabled=" + SwarmConfig.ENABLED.get()
                                + ", neighborRadius=" + SwarmConfig.NEIGHBOR_RADIUS.get()
                                + ", targetRadius=" + SwarmConfig.TARGET_RADIUS.get()
                                + ", formationRadius=" + SwarmConfig.FORMATION_RADIUS.get()
                                + ", formationSlotHysteresisTicks=" + SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.get()
                                + ", roleHysteresisTicks=" + SwarmConfig.ROLE_HYSTERESIS_TICKS.get()
                                + ", divisionOfLaborEnabled=" + SwarmConfig.DIVISION_OF_LABOR_ENABLED.get()
                                + ", specializationMinHoldTicks=" + SwarmConfig.SPECIALIZATION_MIN_HOLD_TICKS.get()
                                + ", specializationExperienceGain=" + SwarmConfig.SPECIALIZATION_EXPERIENCE_GAIN.get()
                                + ", specializationExperienceDecay=" + SwarmConfig.SPECIALIZATION_EXPERIENCE_DECAY.get()
                                + ", alignmentWeight=" + SwarmConfig.ALIGNMENT_WEIGHT.get()
                                + ", maxSteeringCorrection=" + SwarmConfig.MAX_STEERING_CORRECTION.get()
                                + ", staleTargetMinSpeedFactor=" + SwarmConfig.STALE_TARGET_MIN_SPEED_FACTOR.get()
                                + ", searchConfidenceThreshold=" + SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.get()
                                + ", searchMinRadius=" + SwarmConfig.SEARCH_MIN_RADIUS.get()
                                + ", searchMaxRadius=" + SwarmConfig.SEARCH_MAX_RADIUS.get()
                                + ", searchPhaseTicks=" + SwarmConfig.SEARCH_PHASE_TICKS.get()
                                + ", searchSpeedFactor=" + SwarmConfig.SEARCH_SPEED_FACTOR.get()
                                + ", targetPredictionEnabled=" + SwarmConfig.TARGET_PREDICTION_ENABLED.get()
                                + ", targetPredictionLeadTicks=" + SwarmConfig.TARGET_PREDICTION_LEAD_TICKS.get()
                                + ", targetPredictionMaxTicks=" + SwarmConfig.TARGET_PREDICTION_MAX_TICKS.get()
                                + ", targetPredictionMaxDistance=" + SwarmConfig.TARGET_PREDICTION_MAX_DISTANCE.get()
                                + ", navStuckWindowTicks=" + SwarmConfig.NAV_STUCK_WINDOW_TICKS.get()
                                + ", navStuckMinProgress=" + SwarmConfig.NAV_STUCK_MIN_PROGRESS.get()
                                + ", navRecoveryLateralDistance=" + SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.get()
                                + ", navRecoveryDurationTicks=" + SwarmConfig.NAV_RECOVERY_DURATION_TICKS.get()
                                + ", navObstacleAvoidanceEnabled=" + SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.get()
                                + ", navObstacleLookahead=" + SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.get()
                                + ", navObstacleLateralDistance=" + SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.get()
                                + ", navObstacleHoldTicks=" + SwarmConfig.NAV_OBSTACLE_HOLD_TICKS.get()
                                + ", navObstacleArrivalTolerance=" + SwarmConfig.NAV_OBSTACLE_ARRIVAL_TOLERANCE.get()
                                + ", navWalkabilityEnabled=" + SwarmConfig.NAV_WALKABILITY_ENABLED.get()
                                + ", navMaxProbeDropBlocks=" + SwarmConfig.NAV_MAX_PROBE_DROP_BLOCKS.get()
                                + ", navLocalProgressWeight=" + SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.get()
                                + ", navLocalLateralPenalty=" + SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.get()
                                + ", navLocalCongestionPenalty=" + SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.get()
                                + ", navLocalCongestionRadius=" + SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.get()
                                + ", navPathEvidenceEnabled=" + SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get()
                                + ", navPathNodePenalty=" + SwarmConfig.NAV_PATH_NODE_PENALTY.get()
                                + ", navPathResidualPenalty=" + SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.get()
                                + ", navPathMaxResidualDistance=" + SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.get()
                                + ", sensingImperfectionEnabled=" + SwarmConfig.SENSING_IMPERFECTION_ENABLED.get()
                                + ", sensingDropoutRate=" + SwarmConfig.SENSING_DROPOUT_RATE.get()
                                + ", sensingMaxHorizontalNoise=" + SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.get()
                                + ", sensingExperimentSeed=" + SwarmConfig.SENSING_EXPERIMENT_SEED.get()
                                + ", communicationEnabled=" + SwarmConfig.COMMUNICATION_ENABLED.get()
                                + ", communicationRadius=" + SwarmConfig.COMMUNICATION_RADIUS.get()
                                + ", latencyTicks=" + SwarmConfig.COMMUNICATION_LATENCY_TICKS.get()
                                + ", packetDropRate=" + SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.get()
                                + ", experimentSeed=" + SwarmConfig.COMMUNICATION_EXPERIMENT_SEED.get()
                                + ", debugParticles=" + SwarmDebugState.particlesEnabled()
                                + ", heterogeneousAgents=ZOMBIE+SKELETON+SPIDER+CREEPER"
                                + ", externalAI=" + SwarmConfig.EXTERNAL_AI_ENABLED.get()
                ),
                false
        );
        return 1;
    }

    private static int inspectNearest(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double radius = Math.max(16.0, SwarmConfig.NEIGHBOR_RADIUS.get());
        List<PathfinderMob> agents = level.getEntitiesOfClass(
                PathfinderMob.class,
                player.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && SwarmAgentProfiles.isSupported(candidate)
        );

        PathfinderMob nearest = agents.stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);

        if (nearest == null) {
            source.sendFailure(Component.literal("No living supported swarm agent found nearby."));
            return 0;
        }

        SwarmAgentState state = nearest.getData(SwarmAttachments.AGENT_STATE.get());
        long age = state.targetId() == null
                ? -1L
                : Math.max(0L, level.getGameTime() - state.lastTargetObservationTick());
        double confidence = state.targetConfidence(
                level.getGameTime(),
                SwarmConfig.TARGET_MEMORY_TICKS.get()
        );
        TargetObservation observation = state.targetObservation();
        String targetEstimate = observation == null || !observation.hasFinitePosition()
                ? "none"
                : String.format(
                        java.util.Locale.ROOT,
                        "(%.2f, %.2f, %.2f)",
                        observation.x(),
                        observation.y(),
                        observation.z()
                );

        source.sendSuccess(
                () -> Component.literal(
                        "Agent #" + nearest.getId()
                                + " archetype=" + SwarmAgentProfiles.profile(nearest).archetype()
                                + " role=" + state.role()
                                + " tacticalPattern=" + state.tacticalPattern()
                                + " skeletonSpacing=" + state.rangedSpacingActive()
                                + " skeletonSpacingEpisodes=" + state.rangedSpacingEpisodes()
                                + " fillingMissingFlank=" + state.coveringVacantFlank()
                                + " flankFillEpisodes=" + state.vacantFlankCoverageEpisodes()
                                + " searchRallyActive=" + state.searchRallyActive()
                                + " searchRallyEpisodes=" + state.searchRallyEpisodes()
                                + " crowdLane=" + state.crowdLaneSide()
                                + " crowdLaneSamples=" + state.crowdLaneUses()
                                + " crowdLaneRejected=" + state.crowdLaneRejected()
                                + " pendingRole=" + (state.pendingRole() == null ? "none" : state.pendingRole())
                                + " roleReassignments=" + state.roleReassignmentCount()
                                + " mode=" + state.behaviorMode()
                                + " searchRadius=" + String.format(java.util.Locale.ROOT, "%.2f", state.searchRadius())
                                + " slot=" + state.formationSlot()
                                + " pendingSlot=" + state.pendingFormationSlot()
                                + " slotSwitches=" + state.formationSlotSwitchCount()
                                + " neighbors=" + state.neighborCount()
                                + " target=" + (state.targetId() == null ? "none" : state.targetId())
                                + " targetAgeTicks=" + age
                                + " directObservation=" + state.directObservation()
                                + " targetConfidence=" + String.format(java.util.Locale.ROOT, "%.3f", confidence)
                                + " targetEstimate=" + targetEstimate
                                + " predictedTarget=" + (state.hasPrediction()
                                        ? String.format(
                                                java.util.Locale.ROOT,
                                                "(%.2f, %.2f)",
                                                state.predictedTargetX(),
                                                state.predictedTargetZ()
                                        )
                                        : "none")
                                + " predictionOffset=" + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        state.predictionOffset()
                                )
                                + " destination=" + (state.hasDestination()
                                        ? String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", state.destinationX(), state.destinationZ())
                                        : "none")
                                + " navMode=" + state.navigationMode()
                                + " navWaypoint=" + (state.hasNavigationWaypoint()
                                        ? String.format(
                                                java.util.Locale.ROOT,
                                                "(%.2f, %.2f)",
                                                state.navigationWaypointX(),
                                                state.navigationWaypointZ()
                                        )
                                        : "none")
                                + " obstacleDetours=" + state.obstacleDetourCount()
                                + " recoveries=" + state.recoveryCount()
                                + " recoveryPlanAttempts=" + state.recoveryPlanningAttempts()
                                + " recoveryPlanFailures=" + state.recoveryPlanningFailures()
                                + " staleRouteResets=" + state.navigationEpisodeResets()
                                + " plannerContext=" + state.plannerContext()
                                + " plannerCandidates=" + state.plannerCandidateCount()
                                + " plannerBlocked=" + state.plannerBlockedCount()
                                + " plannerUnreachable=" + state.plannerUnreachableCount()
                                + " plannerFeasible=" + state.plannerFeasibleCount()
                                + " plannerSelectedIndex=" + state.plannerSelectedIndex()
                                + " plannerSelectedScore=" + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        state.plannerSelectedScore()
                                )
                                + " plannerPathQueries=" + state.plannerPathQueryCount()
                                + " engineeringCarried=" + state.carriedEngineeringBlockCount()
                                + " engineeringBroken=" + state.engineeringBlocksBroken()
                                + " engineeringPlaced=" + state.engineeringBlocksPlaced()
                                + " engineeringRequests=" + state.engineeringRequestsPublished()
                                + " engineeringClaims=" + state.engineeringTasksClaimed()
                                + " engineeringCompleted=" + state.engineeringTasksCompleted()
                                + " engineeringMaterialsGiven=" + state.engineeringMaterialsGiven()
                                + " engineeringMaterialsReceived=" + state.engineeringMaterialsReceived()
                                + " task=" + state.currentTask()
                                + " specialization=" + state.specialization()
                                + " specializationSwitches=" + state.specializationSwitchCount()
                                + " specializationExperience=" + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        state.taskExperience(state.currentTask())
                                )
                                + " separation=" + String.format(java.util.Locale.ROOT, "%.3f", state.separationMagnitude())
                                + " cohesion=" + String.format(java.util.Locale.ROOT, "%.3f", state.cohesionMagnitude())
                                + " alignment=" + String.format(java.util.Locale.ROOT, "%.3f", state.alignmentMagnitude())
                                + " steering=" + String.format(java.util.Locale.ROOT, "%.3f", state.steeringMagnitude())
                                + " inbox=" + state.pendingTargetMessageCount()
                                + " commAccepted=" + state.communicationAcceptedMessages()
                                + " commDelivered=" + state.communicationDeliveredMessages()
                                + " commDropped=" + state.communicationDroppedMessages()
                                + " sensingAccepted=" + state.sensingAcceptedObservations()
                                + " sensingDropped=" + state.sensingDroppedObservations()
                                + " sensingNoise=" + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        state.lastSensingNoiseMagnitude()
                                )
                ),
                false
        );

        return 1;
    }

    private static int inspectGroup(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double radius = Math.max(24.0, SwarmConfig.NEIGHBOR_RADIUS.get());
        List<PathfinderMob> agents = level.getEntitiesOfClass(
                PathfinderMob.class,
                player.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && SwarmAgentProfiles.isSupported(candidate)
        );

        if (agents.isEmpty()) {
            source.sendFailure(Component.literal("No living supported swarm agents found nearby."));
            return 0;
        }

        java.util.Map<String, Integer> patterns = new java.util.TreeMap<>();
        java.util.EnumMap<dev.swarmmobs.agent.SwarmRole, Integer> roles =
                new java.util.EnumMap<>(dev.swarmmobs.agent.SwarmRole.class);
        java.util.EnumMap<SwarmAgentArchetype, Integer> archetypes =
                new java.util.EnumMap<>(SwarmAgentArchetype.class);
        java.util.EnumMap<dev.swarmmobs.agent.SwarmTaskType, Integer> tasks =
                new java.util.EnumMap<>(dev.swarmmobs.agent.SwarmTaskType.class);
        java.util.EnumMap<dev.swarmmobs.agent.SwarmSpecialization, Integer> specializations =
                new java.util.EnumMap<>(dev.swarmmobs.agent.SwarmSpecialization.class);
        int withTarget = 0;
        int spacingSkeletons = 0;
        long spacingEpisodes = 0L;
        int flankFillers = 0;
        long flankFillEpisodes = 0;
        int searchRallying = 0;
        long searchRallyEpisodes = 0;
        int diverted = 0;
        long diversionSamples = 0;
        long diversionRejects = 0;
        int direct = 0;
        int engageCount = 0;
        int searchCount = 0;
        double searchRadiusSum = 0.0;
        double predictionOffsetSum = 0.0;
        int predictionCount = 0;
        double neighborSum = 0.0;
        double separationSum = 0.0;
        double cohesionSum = 0.0;
        double alignmentSum = 0.0;
        double steeringSum = 0.0;
        double confidenceSum = 0.0;
        long pendingMessages = 0L;
        long acceptedMessages = 0L;
        long deliveredMessages = 0L;
        long droppedMessages = 0L;
        int planNavigation = 0;
        int obstacleNavigation = 0;
        int recoveryNavigation = 0;
        long obstacleDetours = 0L;
        long recoveries = 0L;
        long recoveryPlanAttempts = 0L;
        long recoveryPlanFailures = 0L;
        long staleRouteResets = 0L;
        long plannerPathQueries = 0L;
        int agentsWithPlannerDiagnostics = 0;
        long sensingAccepted = 0L;
        long sensingDropped = 0L;
        double sensingNoiseSum = 0.0;
        int pendingRoleCount = 0;
        long roleReassignments = 0L;
        long engineeringBroken = 0L;
        long engineeringPlaced = 0L;
        long engineeringRequests = 0L;
        long engineeringClaims = 0L;
        long engineeringCompleted = 0L;
        long engineeringMaterialsGiven = 0L;
        long engineeringMaterialsReceived = 0L;
        int carriedEngineeringBlocks = 0;

        for (PathfinderMob agent : agents) {
            SwarmAgentState state = agent.getData(SwarmAttachments.AGENT_STATE.get());
            roles.merge(state.role(), 1, Integer::sum);
            patterns.merge(state.tacticalPattern(), 1, Integer::sum);
            if (state.rangedSpacingActive()) spacingSkeletons++;
            spacingEpisodes += state.rangedSpacingEpisodes();
            if (state.coveringVacantFlank()) flankFillers++;
            flankFillEpisodes += state.vacantFlankCoverageEpisodes();
            if (state.searchRallyActive()) searchRallying++;
            searchRallyEpisodes += state.searchRallyEpisodes();
            if (state.crowdLaneSide() != 0) diverted++;
            diversionSamples += state.crowdLaneUses();
            diversionRejects += state.crowdLaneRejected();
            archetypes.merge(SwarmAgentProfiles.profile(agent).archetype(), 1, Integer::sum);
            tasks.merge(state.currentTask(), 1, Integer::sum);
            specializations.merge(state.specialization(), 1, Integer::sum);
            if (state.targetId() != null) {
                withTarget++;
            }
            if (state.directObservation()) {
                direct++;
            }
            if (state.behaviorMode() == SwarmBehaviorMode.SEARCH) {
                searchCount++;
                searchRadiusSum += state.searchRadius();
            } else {
                engageCount++;
            }
            if (state.hasPrediction()) {
                predictionCount++;
                predictionOffsetSum += state.predictionOffset();
            }
            neighborSum += state.neighborCount();
            separationSum += state.separationMagnitude();
            cohesionSum += state.cohesionMagnitude();
            alignmentSum += state.alignmentMagnitude();
            steeringSum += state.steeringMagnitude();
            confidenceSum += state.targetConfidence(
                    level.getGameTime(),
                    SwarmConfig.TARGET_MEMORY_TICKS.get()
            );
            pendingMessages += state.pendingTargetMessageCount();
            acceptedMessages += state.communicationAcceptedMessages();
            deliveredMessages += state.communicationDeliveredMessages();
            droppedMessages += state.communicationDroppedMessages();
            switch (state.navigationMode()) {
                case PLAN -> planNavigation++;
                case OBSTACLE_DETOUR -> obstacleNavigation++;
                case RECOVERY -> recoveryNavigation++;
            }
            obstacleDetours += state.obstacleDetourCount();
            recoveries += state.recoveryCount();
            recoveryPlanAttempts += state.recoveryPlanningAttempts();
            recoveryPlanFailures += state.recoveryPlanningFailures();
            staleRouteResets += state.navigationEpisodeResets();
            plannerPathQueries += state.plannerPathQueryCount();
            if (state.plannerContext() != SwarmPlannerContext.NONE) {
                agentsWithPlannerDiagnostics++;
            }
            sensingAccepted += state.sensingAcceptedObservations();
            sensingDropped += state.sensingDroppedObservations();
            sensingNoiseSum += state.lastSensingNoiseMagnitude();
            if (state.pendingRole() != null) {
                pendingRoleCount++;
            }
            roleReassignments += state.roleReassignmentCount();
            engineeringBroken += state.engineeringBlocksBroken();
            engineeringPlaced += state.engineeringBlocksPlaced();
            engineeringRequests += state.engineeringRequestsPublished();
            engineeringClaims += state.engineeringTasksClaimed();
            engineeringCompleted += state.engineeringTasksCompleted();
            engineeringMaterialsGiven += state.engineeringMaterialsGiven();
            engineeringMaterialsReceived += state.engineeringMaterialsReceived();
            carriedEngineeringBlocks += Math.max(0, state.carriedEngineeringBlockCount());
        }

        int total = agents.size();
        String roleSummary = java.util.Arrays.stream(dev.swarmmobs.agent.SwarmRole.values())
                .map(role -> role + "=" + roles.getOrDefault(role, 0))
                .collect(java.util.stream.Collectors.joining(", "));

        String archetypeSummary = java.util.Arrays.stream(SwarmAgentArchetype.values())
                .map(archetype -> archetype + "=" + archetypes.getOrDefault(archetype, 0))
                .collect(java.util.stream.Collectors.joining(", "));

        String taskSummary = java.util.Arrays.stream(dev.swarmmobs.agent.SwarmTaskType.values())
                .map(task -> task + "=" + tasks.getOrDefault(task, 0))
                .collect(java.util.stream.Collectors.joining(", "));

        String specializationSummary = java.util.Arrays.stream(dev.swarmmobs.agent.SwarmSpecialization.values())
                .map(spec -> spec + "=" + specializations.getOrDefault(spec, 0))
                .collect(java.util.stream.Collectors.joining(", "));

        String summary = String.format(
                java.util.Locale.ROOT,
                "Swarm group: agents=%d, targetKnown=%d, direct=%d, engage=%d, search=%d, avgSearchRadius=%.2f, predictionActive=%d, avgPredictionOffset=%.3f, avgNeighbors=%.2f, avgSeparation=%.3f, avgCohesion=%.3f, avgAlignment=%.3f, avgSteering=%.3f, avgTargetConfidence=%.3f, pendingMessages=%d, commAccepted=%d, commDelivered=%d, commDropped=%d, navPlan=%d, navDetour=%d, navRecovery=%d, obstacleDetours=%d, recoveries=%d, recoveryPlanAttempts=%d, recoveryPlanFailures=%d, plannerDiagnostics=%d, plannerPathQueries=%d, sensingAccepted=%d, sensingDropped=%d, avgLastSensingNoise=%.3f, pendingRoles=%d, roleReassignments=%d, engineeringBroken=%d, engineeringPlaced=%d, carriedEngineeringBlocks=%d, engineeringRequests=%d, engineeringClaims=%d, engineeringCompleted=%d, engineeringMaterialsGiven=%d, engineeringMaterialsReceived=%d, archetypes={%s}, roles={%s}, tasks={%s}, specializations={%s}",
                total,
                withTarget,
                direct,
                engageCount,
                searchCount,
                searchCount == 0 ? 0.0 : searchRadiusSum / searchCount,
                predictionCount,
                predictionCount == 0 ? 0.0 : predictionOffsetSum / predictionCount,
                neighborSum / total,
                separationSum / total,
                cohesionSum / total,
                alignmentSum / total,
                steeringSum / total,
                confidenceSum / total,
                pendingMessages,
                acceptedMessages,
                deliveredMessages,
                droppedMessages,
                planNavigation,
                obstacleNavigation,
                recoveryNavigation,
                obstacleDetours,
                recoveries,
                recoveryPlanAttempts,
                recoveryPlanFailures,
                agentsWithPlannerDiagnostics,
                plannerPathQueries,
                sensingAccepted,
                sensingDropped,
                sensingNoiseSum / total,
                pendingRoleCount,
                roleReassignments,
                engineeringBroken,
                engineeringPlaced,
                carriedEngineeringBlocks,
                engineeringRequests,
                engineeringClaims,
                engineeringCompleted,
                engineeringMaterialsGiven,
                engineeringMaterialsReceived,
                archetypeSummary,
                roleSummary,
                taskSummary,
                specializationSummary
        );

        // Aggregate of actual per-agent game tactic states, not claimed wins.
        String patternsText = patterns.toString();
        String mobility = ", skeletonSpacing=" + spacingSkeletons + ", spacingEpisodes=" + spacingEpisodes + ", activeFlankFillers=" + flankFillers + ", flankFillEpisodes=" + flankFillEpisodes + ", staleRouteResets=" + staleRouteResets + ", regroupingSearchAgents=" + searchRallying + ", regroupingEpisodes=" + searchRallyEpisodes + ", localLaneDiverted=" + diverted + ", laneSamples=" + diversionSamples + ", blockedLaneFallbacks=" + diversionRejects;
        source.sendSuccess(() -> Component.literal(summary + ", patterns=" + patternsText + mobility), false);
        return total;
    }

    private static int setCommunicationEnabled(CommandSourceStack source, boolean enabled) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.COMMUNICATION_ENABLED.set(enabled);
        source.sendSuccess(
                () -> Component.literal("Swarm communication runtime switch: " + (enabled ? "ON" : "OFF")),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setCommunicationBaseline(CommandSourceStack source) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.COMMUNICATION_ENABLED.set(true);
        SwarmConfig.COMMUNICATION_LATENCY_TICKS.set(0);
        SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(0.0D);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm communication baseline restored: enabled=true, latencyTicks=0, packetDropRate=0.0"
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setCommunicationLatency(CommandSourceStack source, int ticks) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.COMMUNICATION_LATENCY_TICKS.set(ticks);
        source.sendSuccess(
                () -> Component.literal("Swarm communication runtime latencyTicks=" + ticks),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return ticks;
    }

    private static int setCommunicationDropRate(CommandSourceStack source, double rate) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(rate);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm communication runtime packetDropRate="
                                + String.format(java.util.Locale.ROOT, "%.3f", rate)
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setCommunicationRadius(CommandSourceStack source, double radius) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.COMMUNICATION_RADIUS.set(radius);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm communication runtime radius="
                                + String.format(java.util.Locale.ROOT, "%.2f", radius)
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setCommunicationSeed(CommandSourceStack source, int seed) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.COMMUNICATION_EXPERIMENT_SEED.set(seed);
        source.sendSuccess(
                () -> Component.literal("Swarm communication runtime experimentSeed=" + seed),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setSensingEnabled(CommandSourceStack source, boolean enabled) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(enabled);
        source.sendSuccess(
                () -> Component.literal("Swarm sensing imperfections: " + (enabled ? "ON" : "OFF")),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setSensingBaseline(CommandSourceStack source) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(false);
        SwarmConfig.SENSING_DROPOUT_RATE.set(0.0D);
        SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.set(0.0D);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm sensing baseline restored: imperfectionEnabled=false, dropoutRate=0.0, maxHorizontalNoise=0.0"
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setSensingDropoutRate(CommandSourceStack source, double rate) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.SENSING_DROPOUT_RATE.set(rate);
        SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(true);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm sensing runtime dropoutRate="
                                + String.format(java.util.Locale.ROOT, "%.3f", rate)
                                + " (imperfections enabled)"
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setSensingNoise(CommandSourceStack source, double blocks) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.set(blocks);
        SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(true);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm sensing runtime maxHorizontalNoise="
                                + String.format(java.util.Locale.ROOT, "%.3f", blocks)
                                + " blocks (imperfections enabled)"
                ),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int setSensingSeed(CommandSourceStack source, int seed) {
        SwarmExperimentManager.markCustom();
        SwarmConfig.SENSING_EXPERIMENT_SEED.set(seed);
        source.sendSuccess(
                () -> Component.literal("Swarm sensing runtime experimentSeed=" + seed),
                true
        );
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int toggleParticles(CommandSourceStack source) {
        boolean enabled = SwarmDebugState.toggleParticles();
        source.sendSuccess(
                () -> Component.literal("Swarm debug particles: " + (enabled ? "ON" : "OFF")),
                true
        );
        return enabled ? 1 : 0;
    }

    private static int setParticles(CommandSourceStack source, boolean enabled) {
        SwarmDebugState.setParticlesEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("Swarm debug particles: " + (enabled ? "ON" : "OFF")),
                true
        );
        return 1;
    }

    // Opt-in runtime colony test profile; do not activate in a valued world.
    private static int setPlaytestMode(CommandSourceStack source, boolean enabled) {
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
            // Use bounded shorter surveys for observable test-world activity.
            SwarmConfig.NEST_BUILD_INTERVAL_TICKS.set(100);
            SwarmConfig.NEST_GATHER_INTERVAL.set(60);
            SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.set(40);
            SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.set(120);
            SwarmConfig.NEST_HAUL_MAX_STACK.set(64);
            source.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, source.getServer());
            source.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING)
                    .set(true, source.getServer());
        }
        SwarmDebugState.setParticlesEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal(enabled
                        ? "PLAYTEST ON: nest founding, lifecycle, hauling, gathering, hunting, berries, "
                                + "replanting, reproduction and visible shells. World gamerules enabled. "
                                + "Use only a DISPOSABLE world! Colony workers need a nearby core and actual resources."
                        : "PLAYTEST OFF: optional destructive colony jobs and reproduction disabled. "
                                + "GameRules were NOT reverted; check mobGriefing yourself."),
                true);
        // CLI updates must survive world reload like saved control-panel edits.
        SwarmConfig.SPEC.save();
        return 1;
    }

    private static int playtestModeStatus(CommandSourceStack source) {
        boolean grief = source.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean spawning = source.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        source.sendSuccess(() -> Component.literal(
                "Playtest: swarm=" + SwarmConfig.ENABLED.get()
                + ", founding=" + SwarmConfig.NEST_CONSTRUCTION_ENABLED.get()
                + ", lifecycle=" + SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                + ", hauling=" + SwarmConfig.NEST_HAULING_ENABLED.get()
                + ", blockGather=" + SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get()
                + ", animalHunt=" + SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.get()
                + ", berry=" + SwarmConfig.NEST_BERRY_FORAGING_ENABLED.get()
                + ", replant=" + SwarmConfig.NEST_CROP_REPLANT_ENABLED.get()
                + ", shell=" + SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.get()
                + ", adaptiveStock=" + SwarmConfig.NEST_ADAPTIVE_STOCK_ENABLED.get()
                + ", mobGriefing=" + grief + ", doMobSpawning=" + spawning
                + ". Work requires a loaded home core, no combat target, nearby suitable resources."),
                false);
        return 1;
    }

    // Report WHY the nearest Zombie can or cannot perform opt-in colony work.
    private static int nearestWorkerStatus(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run /swarmmobs debug workstatus as a player."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Zombie zombie = level.getEntitiesOfClass(
                Zombie.class, player.getBoundingBox().inflate(24.0),
                candidate -> candidate.isAlive() && !candidate.isNoAi()
        ).stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (zombie == null) {
            source.sendFailure(Component.literal("No living AI-enabled Zombie within 24 blocks."));
            return 0;
        }
        var state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        var data = zombie.getPersistentData();
        boolean assigned = data.contains("SwarmColonyNest");
        boolean homeLoaded = false;
        boolean homeValid = false;
        boolean nearHome = false;
        String homeText = "none";
        if (assigned) {
            BlockPos home = BlockPos.of(data.getLong("SwarmColonyNest"));
            homeText = home.toShortString();
            homeLoaded = level.hasChunkAt(home);
            homeValid = homeLoaded && level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get());
            nearHome = zombie.distanceToSqr(home.getX() + 0.5, home.getY() + 0.5,
                    home.getZ() + 0.5) <= 24.0 * 24.0;
        }
        boolean combat = zombie.getTarget() != null || state.targetId() != null;
        String result = "Worker #" + zombie.getId()
                + ": home=" + homeText + " (loaded=" + homeLoaded
                + ", core=" + homeValid + ", <=24blocks=" + nearHome + ")"
                + ", targetBusy=" + combat
                + ", engagement=" + state.engagementMode()
                + ", lifecycle=" + SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                + ", hauling=" + SwarmConfig.NEST_HAULING_ENABLED.get()
                + ", gathering=" + SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get()
                + ", mobGriefing=" + level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                + ". For work: valid nearby core, no target, WORK mode, available resources.";
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    /** Read-only building inspection; aim at the Nest Core within 48 blocks. */
    private static int inspectLookedAtNest(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        HitResult hit = player.pick(48.0, 0.0F, false);
        if (!(hit instanceof BlockHitResult block)
                || !player.serverLevel().hasChunkAt(block.getBlockPos())
                || !(player.serverLevel().getBlockEntity(block.getBlockPos())
                        instanceof SwarmNestBlockEntity nest)) {
            source.sendFailure(Component.literal(
                    "Aim your crosshair at a loaded Nest Core within 48 blocks."));
            return 0;
        }
        String msg = "Nest @ " + block.getBlockPos().toShortString()
                + ": stock=" + nest.resources() + "/"
                + SwarmNestColonyPolicy.MAX_STORED_RESOURCES
                + " [soil=" + nest.soilPoints()
                + ", timber=" + nest.timberPoints()
                + ", food=" + nest.nutrientPoints()
                + ", legacy=" + nest.legacyPoints() + "]"
                + ", pop=" + nest.lastPopulation() + "/" + nest.effectiveCapacity()
                + ", chambers=" + nest.chamberLevel() + "/"
                + SwarmNestArchitecturePolicy.MAX_CHAMBER_LEVEL
                + ", visibleModules=" + nest.visibleChamberLevel()
                + ", paidRepairs=" + nest.repairedShellPieces()
                + ", births=" + nest.births()
                + ", haulTrips=" + nest.haulTrips()
                + ", hauledItems=" + nest.hauledItems()
                + ", visibleBuildEnabled=" + SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.get()
                + ", lifecycleEnabled=" + SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                + ", mobGriefing=" + player.serverLevel().getGameRules()
                        .getBoolean(GameRules.RULE_MOBGRIEFING)
                + ". Expansion requires near-capacity population and real soil/timber; "
                + "damaged registered shell blocks repair only if their sites are empty "
                + "and enough corresponding materials remain.";
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int spawnMixedSwarm(CommandSourceStack source, int count) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }

        if (player.isCreative() || player.isSpectator()) {
            source.sendFailure(Component.literal(
                    "For a real targeting test, switch to Survival or Adventure before spawning the mixed swarm."
            ));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double radius = 11.0;
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i) / count;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;

            PathfinderMob agent;
            switch (i % 3) {
                case 0 -> agent = EntityType.ZOMBIE.create(level);
                case 1 -> agent = EntityType.SKELETON.create(level);
                default -> agent = EntityType.SPIDER.create(level);
            }

            if (agent == null) {
                continue;
            }

            agent.moveTo(x, player.getY(), z, (float) Math.toDegrees(angle + Math.PI), 0.0F);
            level.addFreshEntity(agent);
            spawned++;
        }

        int finalSpawned = spawned;
        source.sendSuccess(
                () -> Component.literal(
                        "Spawned " + finalSpawned
                                + " mixed swarm agents (Zombie assault + Skeleton ranged support + Spider flankers)."
                                + " Use /swarmmobs group to inspect cooperation."
                ),
                true
        );
        return spawned;
    }

    private static int spawnTestSwarm(CommandSourceStack source, int count) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }

        if (player.isCreative() || player.isSpectator()) {
            source.sendFailure(Component.literal(
                    "For a real targeting test, switch to Survival or Adventure before spawning the swarm."
            ));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double radius = 10.0;

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i) / count;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;

            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null) {
                continue;
            }

            zombie.moveTo(x, player.getY(), z, (float) Math.toDegrees(angle + Math.PI), 0.0F);
            level.addFreshEntity(zombie);
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Spawned " + count + " zombies in a test ring. Use /swarmmobs inspect to read the nearest agent."
                ),
                true
        );
        return count;
    }

    private SwarmCommands() {}
}
