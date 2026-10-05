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
        return type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.SPIDER;
    }

    public static SwarmAgentProfile profile(PathfinderMob mob) {
        if (mob.getType() == EntityType.SKELETON) {
            return SwarmAgentProfile.rangedSupport();
        }
        if (mob.getType() == EntityType.SPIDER) {
            return SwarmAgentProfile.flanker();
        }
        return SwarmAgentProfile.assault();
    }

    public static SwarmRole tacticalRole(
            SwarmAgentArchetype archetype,
            int stableSlot
    ) {
        return SwarmTacticalRolePolicy.roleFor(archetype, stableSlot);
    }

    public static SwarmRole tacticalRole(
            SwarmAgentArchetype archetype,
            int stableSlot,
            SwarmLocalComposition composition
    ) {
        return SwarmTacticalRolePolicy.roleFor(archetype, stableSlot, composition);
    }

    private SwarmAgentProfiles() {}
}
