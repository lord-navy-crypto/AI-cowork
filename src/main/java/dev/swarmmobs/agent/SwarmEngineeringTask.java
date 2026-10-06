package dev.swarmmobs.agent;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Transient local engineering task shared between nearby Zombie agents.
 */
public record SwarmEngineeringTask(
        Type type,
        UUID requesterId,
        UUID claimantId,
        BlockPos position,
        long createdTick,
        long expiresTick
) {
    public enum Type {
        BREAK,
        BRIDGE
    }

    public boolean active(long gameTick) {
        return type != null
                && requesterId != null
                && claimantId != null
                && position != null
                && gameTick < expiresTick;
    }

    public boolean claimedBy(UUID entityId, long gameTick) {
        return active(gameTick)
                && entityId != null
                && claimantId.equals(entityId);
    }
}
