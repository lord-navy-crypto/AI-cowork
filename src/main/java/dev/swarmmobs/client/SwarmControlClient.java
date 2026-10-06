package dev.swarmmobs.client;

import dev.swarmmobs.network.ControlPanelActionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SwarmControlClient {

    public static void acceptSnapshot(String data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SwarmControlScreen screen) {
            screen.updateSnapshot(data);
        } else {
            minecraft.setScreen(new SwarmControlScreen(data));
        }
    }

    public static void sendAction(String action, double value) {
        PacketDistributor.sendToServer(new ControlPanelActionPayload(action, value));
    }

    private SwarmControlClient() {}
}
