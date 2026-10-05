package dev.swarmmobs.data;

import dev.swarmmobs.SwarmMobs;
import dev.swarmmobs.agent.SwarmAgentState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class SwarmAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SwarmMobs.MOD_ID);

    public static final Supplier<AttachmentType<SwarmAgentState>> AGENT_STATE =
            ATTACHMENT_TYPES.register(
                    "agent_state",
                    () -> AttachmentType.builder(SwarmAgentState::new).build()
            );

    private SwarmAttachments() {}
}
