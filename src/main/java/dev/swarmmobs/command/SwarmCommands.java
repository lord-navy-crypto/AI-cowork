package dev.swarmmobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
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
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(2, 32))
                                                .executes(context -> spawnTestSwarm(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                )))))
        );
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Swarm Mobs: enabled=" + SwarmConfig.ENABLED.get()
                                + ", neighborRadius=" + SwarmConfig.NEIGHBOR_RADIUS.get()
                                + ", targetRadius=" + SwarmConfig.TARGET_RADIUS.get()
                                + ", formationRadius=" + SwarmConfig.FORMATION_RADIUS.get()
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
                ),
                false
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
