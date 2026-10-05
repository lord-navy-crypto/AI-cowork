package dev.swarmmobs;

import dev.swarmmobs.command.SwarmCommands;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.event.SwarmMobEvents;
import dev.swarmmobs.gametest.SwarmGameTestRegistration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(SwarmMobs.MOD_ID)
public final class SwarmMobs {
    public static final String MOD_ID = "swarmmobs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SwarmMobs(IEventBus modBus, ModContainer modContainer) {
        SwarmAttachments.ATTACHMENT_TYPES.register(modBus);
        modBus.addListener(SwarmGameTestRegistration::register);
        modContainer.registerConfig(ModConfig.Type.SERVER, SwarmConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(SwarmMobEvents::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(SwarmMobEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(SwarmCommands::onRegisterCommands);

        LOGGER.info("Swarm Mobs {} initialized: deterministic swarm active; local Ollama strategy interface available and OFF by default.",
                modContainer.getModInfo().getVersion());
    }
}
