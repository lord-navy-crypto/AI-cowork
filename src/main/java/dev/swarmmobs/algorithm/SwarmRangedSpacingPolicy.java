package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

/**
 * Minecraft Skeleton comfort spacing. When an actually visible game player
 * is too close, consider a small retreat waypoint without modifying vanilla
 * projectile attacks or inventing target observations.
 */
public final class SwarmRangedSpacingPolicy {
    public static final double START_RETREAT_BLOCKS = 6.0;
    public static final double FINISH_RETREAT_BLOCKS = 7.5;
    public static final double STEP_BLOCKS = 3.0;
    public record Plan(Vec2 candidate, boolean active) {}

    public static Plan consider(Vec2 skeleton, Vec2 observedTarget,
                                boolean directSight, double confidence) {
        Plan no = new Plan(skeleton, false);
        if (!directSight || !finite(skeleton) || !finite(observedTarget)
                || !Double.isFinite(confidence) || confidence < 0.7) return no;
        Vec2 away = skeleton.subtract(observedTarget);
        double distance = away.length();
        if (distance < 0.15 || distance >= START_RETREAT_BLOCKS) return no;
        Vec2 candidate = skeleton.add(away.scale(
                Math.min(STEP_BLOCKS, FINISH_RETREAT_BLOCKS-distance) / distance));
        return new Plan(candidate, true);
    }

    private static boolean finite(Vec2 v) {
        return v != null && Double.isFinite(v.x()) && Double.isFinite(v.z());
    }
    private SwarmRangedSpacingPolicy() {}
}
