package dev.swarmmobs.agent;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Capability mapping for supported heterogeneous swarm members.
 *
 * The first heterogeneous milestone intentionally supports only vanilla Zombies
 * and Skeletons. More species can be added here without changing the planner API.
 */
public final class SwarmAgentProfiles {

    public static boolean isSupported(PathfinderMob mob) {
        return mob != null && isSupportedType(mob.getType());
    }

    public static boolean isSupportedType(EntityType<?> type) {
        return type == EntityType.ZOMBIE || type == EntityType.SKELETON;
    }

    public static SwarmAgentProfile profile(PathfinderMob mob) {
        if (mob.getType() == EntityType.SKELETON) {
            return SwarmAgentProfile.rangedSupport();
        }
        return SwarmAgentProfile.assault();
    }

    public static SwarmRole tacticalRole(
            SwarmAgentArchetype archetype,
            int stableSlot
    ) {
        if (archetype == SwarmAgentArchetype.RANGED_SUPPORT) {
            return SwarmRole.RANGED_SUPPORT;
        }

        return switch (Math.floorMod(stableSlot, 4)) {
            case 0 -> SwarmRole.CHASER;
            case 1 -> SwarmRole.FLANK_LEFT;
            case 2 -> SwarmRole.FLANK_RIGHT;
            default -> SwarmRole.REAR_PRESSURE;
        };
    }

    private SwarmAgentProfiles() {}
}
