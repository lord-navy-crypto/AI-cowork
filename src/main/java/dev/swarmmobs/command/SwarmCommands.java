package dev.swarmmobs.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.swarmmobs.config.SwarmConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SwarmCommands {

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("swarmmobs")
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
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
                                }))
        );
    }

    private SwarmCommands() {}
}
