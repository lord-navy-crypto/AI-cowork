package dev.swarmmobs.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.swarmmobs.SwarmMobs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = SwarmMobs.MOD_ID, value = Dist.CLIENT)
public final class SwarmClientEvents {
    private static final ResourceLocation HUD_LAYER =
            ResourceLocation.fromNamespaceAndPath(SwarmMobs.MOD_ID, "command_center_entry");

    private static final KeyMapping OPEN_COMMAND_CENTER = new KeyMapping(
            "key.swarmmobs.open_command_center",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.swarmmobs"
    );

    private static final int HUD_X = 6;
    private static final int HUD_Y = 6;
    private static final int HUD_WIDTH = 78;
    private static final int HUD_HEIGHT = 20;

    private SwarmClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (OPEN_COMMAND_CENTER.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                SwarmControlClient.requestPanel();
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || SwarmControlClient.isControlScreen(event.getScreen())) {
            return;
        }

        event.addListener(
                Button.builder(
                        net.minecraft.network.chat.Component.literal("SWARM"),
                        button -> SwarmControlClient.requestPanel()
                ).bounds(HUD_X, HUD_Y, HUD_WIDTH, HUD_HEIGHT).build()
        );
    }

    private static void renderCommandCenterEntry(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }

        guiGraphics.fill(
                HUD_X,
                HUD_Y,
                HUD_X + HUD_WIDTH,
                HUD_Y + HUD_HEIGHT,
                0xB0101820
        );
        guiGraphics.fill(
                HUD_X,
                HUD_Y,
                HUD_X + 3,
                HUD_Y + HUD_HEIGHT,
                0xFF55FF88
        );
        guiGraphics.drawString(
                minecraft.font,
                "SWARM [O]",
                HUD_X + 9,
                HUD_Y + 6,
                0xFFFFFFFF,
                true
        );
    }

    @EventBusSubscriber(modid = SwarmMobs.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        private ModBusEvents() {}

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_COMMAND_CENTER);
        }

        @SubscribeEvent
        public static void registerGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(HUD_LAYER, SwarmClientEvents::renderCommandCenterEntry);
        }
    }
}
