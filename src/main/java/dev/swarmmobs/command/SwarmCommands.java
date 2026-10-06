package dev.swarmmobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.debug.SwarmDebugState;
import dev.swarmmobs.network.SwarmControlNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
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
        root.then(debug);
        dispatcher.register(root);
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
                        "Swarm Mobs: enabled=" + SwarmConfig.ENABLED.get()
                                + ", neighborRadius=" + SwarmConfig.NEIGHBOR_RADIUS.get()
                                + ", targetRadius=" + SwarmConfig.TARGET_RADIUS.get()
                                + ", formationRadius=" + SwarmConfig.FORMATION_RADIUS.get()
                                + ", formationSlotHysteresisTicks=" + SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.get()
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
                                + ", heterogeneousAgents=ZOMBIE+SKELETON+SPIDER"
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

        java.util.EnumMap<dev.swarmmobs.agent.SwarmRole, Integer> roles =
                new java.util.EnumMap<>(dev.swarmmobs.agent.SwarmRole.class);
        java.util.EnumMap<SwarmAgentArchetype, Integer> archetypes =
                new java.util.EnumMap<>(SwarmAgentArchetype.class);
        int withTarget = 0;
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
        long sensingAccepted = 0L;
        long sensingDropped = 0L;
        double sensingNoiseSum = 0.0;

        for (PathfinderMob agent : agents) {
            SwarmAgentState state = agent.getData(SwarmAttachments.AGENT_STATE.get());
            roles.merge(state.role(), 1, Integer::sum);
            archetypes.merge(SwarmAgentProfiles.profile(agent).archetype(), 1, Integer::sum);
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
            sensingAccepted += state.sensingAcceptedObservations();
            sensingDropped += state.sensingDroppedObservations();
            sensingNoiseSum += state.lastSensingNoiseMagnitude();
        }

        int total = agents.size();
        String roleSummary = java.util.Arrays.stream(dev.swarmmobs.agent.SwarmRole.values())
                .map(role -> role + "=" + roles.getOrDefault(role, 0))
                .collect(java.util.stream.Collectors.joining(", "));

        String archetypeSummary = java.util.Arrays.stream(SwarmAgentArchetype.values())
                .map(archetype -> archetype + "=" + archetypes.getOrDefault(archetype, 0))
                .collect(java.util.stream.Collectors.joining(", "));

        String summary = String.format(
                java.util.Locale.ROOT,
                "Swarm group: agents=%d, targetKnown=%d, direct=%d, engage=%d, search=%d, avgSearchRadius=%.2f, predictionActive=%d, avgPredictionOffset=%.3f, avgNeighbors=%.2f, avgSeparation=%.3f, avgCohesion=%.3f, avgAlignment=%.3f, avgSteering=%.3f, avgTargetConfidence=%.3f, pendingMessages=%d, commAccepted=%d, commDelivered=%d, commDropped=%d, navPlan=%d, navDetour=%d, navRecovery=%d, obstacleDetours=%d, recoveries=%d, sensingAccepted=%d, sensingDropped=%d, avgLastSensingNoise=%.3f, archetypes={%s}, roles={%s}",
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
                sensingAccepted,
                sensingDropped,
                sensingNoiseSum / total,
                archetypeSummary,
                roleSummary
        );

        source.sendSuccess(() -> Component.literal(summary), false);
        return total;
    }

    private static int setCommunicationEnabled(CommandSourceStack source, boolean enabled) {
        SwarmConfig.COMMUNICATION_ENABLED.set(enabled);
        source.sendSuccess(
                () -> Component.literal("Swarm communication runtime switch: " + (enabled ? "ON" : "OFF")),
                true
        );
        return 1;
    }

    private static int setCommunicationBaseline(CommandSourceStack source) {
        SwarmConfig.COMMUNICATION_ENABLED.set(true);
        SwarmConfig.COMMUNICATION_LATENCY_TICKS.set(0);
        SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(0.0D);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm communication baseline restored: enabled=true, latencyTicks=0, packetDropRate=0.0"
                ),
                true
        );
        return 1;
    }

    private static int setCommunicationLatency(CommandSourceStack source, int ticks) {
        SwarmConfig.COMMUNICATION_LATENCY_TICKS.set(ticks);
        source.sendSuccess(
                () -> Component.literal("Swarm communication runtime latencyTicks=" + ticks),
                true
        );
        return ticks;
    }

    private static int setCommunicationDropRate(CommandSourceStack source, double rate) {
        SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.set(rate);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm communication runtime packetDropRate="
                                + String.format(java.util.Locale.ROOT, "%.3f", rate)
                ),
                true
        );
        return 1;
    }

    private static int setCommunicationRadius(CommandSourceStack source, double radius) {
        SwarmConfig.COMMUNICATION_RADIUS.set(radius);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm communication runtime radius="
                                + String.format(java.util.Locale.ROOT, "%.2f", radius)
                ),
                true
        );
        return 1;
    }

    private static int setCommunicationSeed(CommandSourceStack source, int seed) {
        SwarmConfig.COMMUNICATION_EXPERIMENT_SEED.set(seed);
        source.sendSuccess(
                () -> Component.literal("Swarm communication runtime experimentSeed=" + seed),
                true
        );
        return 1;
    }

    private static int setSensingEnabled(CommandSourceStack source, boolean enabled) {
        SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(enabled);
        source.sendSuccess(
                () -> Component.literal("Swarm sensing imperfections: " + (enabled ? "ON" : "OFF")),
                true
        );
        return 1;
    }

    private static int setSensingBaseline(CommandSourceStack source) {
        SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(false);
        SwarmConfig.SENSING_DROPOUT_RATE.set(0.0D);
        SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.set(0.0D);
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm sensing baseline restored: imperfectionEnabled=false, dropoutRate=0.0, maxHorizontalNoise=0.0"
                ),
                true
        );
        return 1;
    }

    private static int setSensingDropoutRate(CommandSourceStack source, double rate) {
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
        return 1;
    }

    private static int setSensingNoise(CommandSourceStack source, double blocks) {
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
        return 1;
    }

    private static int setSensingSeed(CommandSourceStack source, int seed) {
        SwarmConfig.SENSING_EXPERIMENT_SEED.set(seed);
        source.sendSuccess(
                () -> Component.literal("Swarm sensing runtime experimentSeed=" + seed),
                true
        );
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
