package dev.swarmmobs.network;

import dev.swarmmobs.SwarmMobs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ControlPanelSnapshotPayload(String data) implements CustomPacketPayload {
    public static final Type<ControlPanelSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SwarmMobs.MOD_ID, "control_panel_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControlPanelSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    ControlPanelSnapshotPayload::data,
                    ControlPanelSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
