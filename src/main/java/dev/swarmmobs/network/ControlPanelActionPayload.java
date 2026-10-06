package dev.swarmmobs.network;

import dev.swarmmobs.SwarmMobs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ControlPanelActionPayload(String action, double value) implements CustomPacketPayload {
    public static final Type<ControlPanelActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SwarmMobs.MOD_ID, "control_panel_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControlPanelActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    ControlPanelActionPayload::action,
                    ByteBufCodecs.DOUBLE,
                    ControlPanelActionPayload::value,
                    ControlPanelActionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
