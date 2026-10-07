package dev.swarmmobs.client;

import dev.swarmmobs.network.ControlPanelActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SwarmControlClient {
    private static Screen pendingParent;
    private static Screen activeControlScreen;
    private static String latestSnapshot = "";

    public static void acceptSnapshot(String data) {
        Minecraft minecraft = Minecraft.getInstance();
        latestSnapshot = data == null ? "" : data;

        // Do not tear down a Cloth screen while its Save consumers are sending
        // multiple server-authoritative actions. The next open will use the fresh
        // snapshot returned by the server.
        if (minecraft.screen == activeControlScreen && activeControlScreen != null) {
            return;
        }

        activeControlScreen = SwarmClothControlScreen.create(pendingParent, latestSnapshot);
        pendingParent = null;
        minecraft.setScreen(activeControlScreen);
    }

    public static void sendAction(String action, double value) {
        PacketDistributor.sendToServer(new ControlPanelActionPayload(action, value));
    }

    public static void requestPanel() {
        Minecraft minecraft = Minecraft.getInstance();
        pendingParent = minecraft.screen;
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
