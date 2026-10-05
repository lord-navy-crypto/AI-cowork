package dev.swarmmobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.ai.SwarmStrategyRequest;
import dev.swarmmobs.ai.SwarmStrategyService;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.debug.SwarmDebugState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
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
                        .executes(context -> inspectGroup(context.getSource())));

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
        root.then(debug);

        var ai = Commands.literal("ai")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(context -> aiStatus(context.getSource())))
                .then(Commands.literal("models")
                        .executes(context -> aiModels(context.getSource())))
                .then(Commands.literal("test")
                        .executes(context -> aiTest(context.getSource())))
                .then(Commands.literal("on")
                        .executes(context -> setAiEnabled(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setAiEnabled(context.getSource(), false)))
                .then(Commands.literal("model")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> setAiModel(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))));

        root.then(ai);
        dispatcher.register(root);
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm Mobs: enabled=" + SwarmConfig.ENABLED.get()
                                + ", neighborRadius=" + SwarmConfig.NEIGHBOR_RADIUS.get()
                                + ", targetRadius=" + SwarmConfig.TARGET_RADIUS.get()
                                + ", formationRadius=" + SwarmConfig.FORMATION_RADIUS.get()
                                + ", communicationEnabled=" + SwarmConfig.COMMUNICATION_ENABLED.get()
                                + ", communicationRadius=" + SwarmConfig.COMMUNICATION_RADIUS.get()
                                + ", latencyTicks=" + SwarmConfig.COMMUNICATION_LATENCY_TICKS.get()
                                + ", packetDropRate=" + SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.get()
                                + ", experimentSeed=" + SwarmConfig.COMMUNICATION_EXPERIMENT_SEED.get()
                                + ", debugParticles=" + SwarmDebugState.particlesEnabled()
                                + ", externalAI=" + SwarmConfig.EXTERNAL_AI_ENABLED.get()
                                + ", ollamaModel=" + (SwarmConfig.OLLAMA_MODEL.get().isBlank()
                                        ? "<not-selected>"
                                        : SwarmConfig.OLLAMA_MODEL.get())
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
        List<Zombie> zombies = level.getEntitiesOfClass(
                Zombie.class,
                player.getBoundingBox().inflate(radius),
                Zombie::isAlive
        );

        Zombie nearest = zombies.stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);

        if (nearest == null) {
            source.sendFailure(Component.literal("No living zombie swarm candidate found nearby."));
            return 0;
        }

        SwarmAgentState state = nearest.getData(SwarmAttachments.AGENT_STATE.get());
        long age = state.targetId() == null
                ? -1L
                : Math.max(0L, level.getGameTime() - state.lastTargetObservationTick());

        source.sendSuccess(
                () -> Component.literal(
                        "Zombie #" + nearest.getId()
                                + " role=" + state.role()
                                + " slot=" + state.formationSlot()
                                + " neighbors=" + state.neighborCount()
                                + " target=" + (state.targetId() == null ? "none" : state.targetId())
                                + " targetAgeTicks=" + age
                                + " directObservation=" + state.directObservation()
                                + " destination=" + (state.hasDestination()
                                        ? String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", state.destinationX(), state.destinationZ())
                                        : "none")
                                + " separation=" + String.format(java.util.Locale.ROOT, "%.3f", state.separationMagnitude())
                                + " cohesion=" + String.format(java.util.Locale.ROOT, "%.3f", state.cohesionMagnitude())
                                + " inbox=" + state.pendingTargetMessageCount()
                                + " commAccepted=" + state.communicationAcceptedMessages()
                                + " commDelivered=" + state.communicationDeliveredMessages()
                                + " commDropped=" + state.communicationDroppedMessages()
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
        List<Zombie> zombies = level.getEntitiesOfClass(
                Zombie.class,
                player.getBoundingBox().inflate(radius),
                Zombie::isAlive
        );

        if (zombies.isEmpty()) {
            source.sendFailure(Component.literal("No living zombie swarm candidates found nearby."));
            return 0;
        }

        java.util.EnumMap<dev.swarmmobs.agent.SwarmRole, Integer> roles =
                new java.util.EnumMap<>(dev.swarmmobs.agent.SwarmRole.class);
        int withTarget = 0;
        int direct = 0;
        double neighborSum = 0.0;
        double separationSum = 0.0;
        double cohesionSum = 0.0;
        long pendingMessages = 0L;
        long acceptedMessages = 0L;
        long deliveredMessages = 0L;
        long droppedMessages = 0L;

        for (Zombie zombie : zombies) {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            roles.merge(state.role(), 1, Integer::sum);
            if (state.targetId() != null) {
                withTarget++;
            }
            if (state.directObservation()) {
                direct++;
            }
            neighborSum += state.neighborCount();
            separationSum += state.separationMagnitude();
            cohesionSum += state.cohesionMagnitude();
            pendingMessages += state.pendingTargetMessageCount();
            acceptedMessages += state.communicationAcceptedMessages();
            deliveredMessages += state.communicationDeliveredMessages();
            droppedMessages += state.communicationDroppedMessages();
        }

        int total = zombies.size();
        String roleSummary = java.util.Arrays.stream(dev.swarmmobs.agent.SwarmRole.values())
                .map(role -> role + "=" + roles.getOrDefault(role, 0))
                .collect(java.util.stream.Collectors.joining(", "));

        String summary = String.format(
                java.util.Locale.ROOT,
                "Swarm group: agents=%d, targetKnown=%d, direct=%d, avgNeighbors=%.2f, avgSeparation=%.3f, avgCohesion=%.3f, pendingMessages=%d, commAccepted=%d, commDelivered=%d, commDropped=%d, roles={%s}",
                total,
                withTarget,
                direct,
                neighborSum / total,
                separationSum / total,
                cohesionSum / total,
                pendingMessages,
                acceptedMessages,
                deliveredMessages,
                droppedMessages,
                roleSummary
        );

        source.sendSuccess(() -> Component.literal(summary), false);
        return total;
    }

    private static int aiStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Checking local Ollama at " + SwarmConfig.OLLAMA_BASE_URL.get() + " ..."
                ),
                false
        );

        SwarmStrategyService.ollama().status().whenComplete((status, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal(
                                "Ollama status failed: " + conciseError(error)
                        ));
                        return;
                    }

                    source.sendSuccess(
                            () -> Component.literal(
                                    "Ollama available=" + status.available()
                                            + ", endpoint=" + status.endpoint()
                                            + ", model=" + (status.model() == null || status.model().isBlank()
                                                    ? "<not-selected>"
                                                    : status.model())
                                            + ", message=" + status.message()
                            ),
                            false
                    );
                })
        );
        return 1;
    }

    private static int aiModels(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Querying local Ollama models asynchronously ..."),
                false
        );

        SwarmStrategyService.ollama().listModels().whenComplete((models, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal(
                                "Could not list Ollama models: " + conciseError(error)
                        ));
                        return;
                    }

                    String result = models.isEmpty()
                            ? "<none>"
                            : String.join(", ", models);
                    source.sendSuccess(
                            () -> Component.literal("Local Ollama models: " + result),
                            false
                    );
                })
        );
        return 1;
    }

    private static int aiTest(CommandSourceStack source) {
        String model = SwarmConfig.OLLAMA_MODEL.get();
        if (model == null || model.isBlank()) {
            source.sendFailure(Component.literal(
                    "No Ollama model selected. Use /swarmmobs ai models, then /swarmmobs ai model <name>."
            ));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Sending one non-blocking interface test to local Ollama model " + model + " ..."
                ),
                false
        );

        SwarmStrategyService.ollama().decide(SwarmStrategyRequest.demo())
                .whenComplete((decision, error) ->
                        source.getServer().execute(() -> {
                            if (error != null) {
                                source.sendFailure(Component.literal(
                                        "Ollama strategy test failed: " + conciseError(error)
                                ));
                                return;
                            }

                            source.sendSuccess(
                                    () -> Component.literal(
                                            "Ollama test decision: mode=" + decision.mode()
                                                    + ", formationRadiusMultiplier="
                                                    + String.format(java.util.Locale.ROOT, "%.2f", decision.formationRadiusMultiplier())
                                                    + ", separationMultiplier="
                                                    + String.format(java.util.Locale.ROOT, "%.2f", decision.separationMultiplier())
                                                    + ", cohesionMultiplier="
                                                    + String.format(java.util.Locale.ROOT, "%.2f", decision.cohesionMultiplier())
                                                    + ", rationale=" + decision.rationale()
                                                    + " [NOT applied to gameplay]"
                                    ),
                                    false
                            );
                        })
                );
        return 1;
    }

    private static int setAiEnabled(CommandSourceStack source, boolean enabled) {
        SwarmConfig.EXTERNAL_AI_ENABLED.set(enabled);
        source.sendSuccess(
                () -> Component.literal(
                        "High-level AI strategy provider switch: " + (enabled ? "ON" : "OFF")
                                + ". v0.2 interface decisions are not yet applied to mob movement."
                ),
                true
        );
        return 1;
    }

    private static int setAiModel(CommandSourceStack source, String model) {
        String normalized = model == null ? "" : model.trim();
        if (normalized.isBlank()) {
            source.sendFailure(Component.literal("Model name cannot be empty."));
            return 0;
        }

        SwarmConfig.OLLAMA_MODEL.set(normalized);
        source.sendSuccess(
                () -> Component.literal(
                        "Selected local Ollama model: " + normalized
                                + ". Use /swarmmobs ai test to verify the interface."
                ),
                true
        );
        return 1;
    }

    private static String conciseError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
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
