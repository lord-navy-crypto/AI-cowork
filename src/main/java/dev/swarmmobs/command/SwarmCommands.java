package dev.swarmmobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.swarmmobs.agent.SwarmAgentState;
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
        dispatcher.register(
                Commands.literal("swarmmobs")
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("inspect")
                                .executes(context -> inspectNearest(context.getSource())))
                        .then(Commands.literal("group")
                                .executes(context -> inspectGroup(context.getSource())))
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(2, 32))
                                                .executes(context -> spawnTestSwarm(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                ))))
                                .then(Commands.literal("particles")
                                        .then(Commands.literal("toggle")
                                                .executes(context -> toggleParticles(context.getSource())))
                                        .then(Commands.literal("on")
                                                .executes(context -> setParticles(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setParticles(context.getSource(), false)))))
        );
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
