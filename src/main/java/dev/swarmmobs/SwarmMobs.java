package dev.swarmmobs;

import dev.swarmmobs.command.SwarmCommands;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.event.SwarmMobEvents;
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
        modContainer.registerConfig(ModConfig.Type.SERVER, SwarmConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(SwarmMobEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(SwarmCommands::onRegisterCommands);

        LOGGER.info("Swarm Mobs {} initialized: algorithmic swarm control active, external AI reserved/off by default.",
                modContainer.getModInfo().getVersion());
    }
}
