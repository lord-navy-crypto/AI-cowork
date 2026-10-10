package dev.swarmmobs.client;

import dev.swarmmobs.network.ControlPanelActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SwarmControlClient {
    private static Screen pendingParent;
    private static Screen activeControlScreen;
    private static String latestSnapshot = "";
    private static boolean openRequested;

    public static void acceptSnapshot(String data) {
        Minecraft minecraft = Minecraft.getInstance();
        latestSnapshot = data == null ? "" : data;

        // Action responses refresh our cached snapshot but must never reopen a
        // screen the player just saved/closed. Only an explicit requestPanel()
        // is allowed to create a new Cloth screen.
        if (minecraft.screen == activeControlScreen && activeControlScreen != null) {
            return;
        }
        if (!openRequested) {
            return;
        }

        activeControlScreen = SwarmClothControlScreen.create(pendingParent, latestSnapshot);
        pendingParent = null;
        openRequested = false;
        minecraft.setScreen(activeControlScreen);
    }

    public static void sendAction(String action, double value) {
        PacketDistributor.sendToServer(new ControlPanelActionPayload(action, value));
    }

    public static void requestPanel() {
        Minecraft minecraft = Minecraft.getInstance();
        pendingParent = minecraft.screen;
        openRequested = true;
        sendAction("panel_refresh", 0.0);
    }

    public static boolean isControlScreen(Screen screen) {
        return screen != null && screen == activeControlScreen;
    }

    public static String latestSnapshot() {
        return latestSnapshot;
    }

    private SwarmControlClient() {}
}
