package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;

/**
 * Let a Minecraft Zombie finish a *short* local flank waypoint just outside
 * vanilla melee range, then always hand movement back for normal attacks.
 * No new attack damage, speed boost, or pursuit through unseen targets.
 */
public final class SwarmZombieFlankHandoffPolicy {
    public static final double MELEE_RELEASE_BLOCKS = 2.5;
    public static final double FLANK_WAYPOINT_TOLERANCE = 1.2;

    public static boolean finishWaypointBeforeMelee(
            SwarmRole role, boolean direct, boolean visible,
            double targetDistanceSqr, double waypointDistanceSqr,
            double normalReleaseBlocks) {
        if (!direct || !visible || (role != SwarmRole.FLANK_LEFT
                && role != SwarmRole.FLANK_RIGHT)
                || !Double.isFinite(targetDistanceSqr)
                || !Double.isFinite(waypointDistanceSqr)
                || !Double.isFinite(normalReleaseBlocks)) return false;
        double cutoff = Math.max(0.0, normalReleaseBlocks);
        return targetDistanceSqr > MELEE_RELEASE_BLOCKS * MELEE_RELEASE_BLOCKS
                && targetDistanceSqr <= cutoff * cutoff
                && waypointDistanceSqr >
                        FLANK_WAYPOINT_TOLERANCE * FLANK_WAYPOINT_TOLERANCE;
    }

    private SwarmZombieFlankHandoffPolicy() {}
}
