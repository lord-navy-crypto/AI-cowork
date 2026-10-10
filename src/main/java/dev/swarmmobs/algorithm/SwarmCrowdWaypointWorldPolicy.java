package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Very cheap local terrain guard before a Minecraft NPC adopts a sideways
 * waypoint. It samples only already loaded blocks: no path search,
 * global entity enumeration or chunk generation.
 */
public final class SwarmCrowdWaypointWorldPolicy {

    public static boolean locallyTraversable(ServerLevel level, double feetY, Vec2 point) {
        if (level == null || point == null || !Double.isFinite(feetY)
                || !Double.isFinite(point.x()) || !Double.isFinite(point.z())) {
            return false;
        }
        BlockPos feet = BlockPos.containing(point.x(), feetY, point.z());
        BlockPos head = feet.above();
        BlockPos ground = feet.below();
        if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(head)
                || !level.isInWorldBounds(ground) || !level.hasChunkAt(feet)) {
            return false;
        }
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP);
    }

    private SwarmCrowdWaypointWorldPolicy() {}
}
