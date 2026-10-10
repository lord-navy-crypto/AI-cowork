package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

/**
 * Small Minecraft Skeleton sidestep when a known same-target Zombie or
 * Creeper obstructs the current arrow corridor. Client rendering and vanilla
 * projectile mechanics remain untouched.
 */
public final class SwarmSkeletonSightlinePolicy {
    public static final double SIDESTEP_BLOCKS = 2.5;
    public record Candidates(Vec2 left, Vec2 right, boolean valid) {}

    public static Candidates propose(Vec2 skeleton, Vec2 visibleTarget) {
        if (!finite(skeleton) || !finite(visibleTarget)) {
            return new Candidates(skeleton, skeleton, false);
        }
        Vec2 toward = visibleTarget.subtract(skeleton);
        double length = toward.length();
        if (length < 0.75) return new Candidates(skeleton, skeleton, false);
        Vec2 side = new Vec2(-toward.z() / length, toward.x() / length);
        return new Candidates(skeleton.add(side.scale(SIDESTEP_BLOCKS)),
                skeleton.add(side.scale(-SIDESTEP_BLOCKS)), true);
    }

    public static Vec2 choose(Candidates pair, boolean leftFeasible,
            boolean rightFeasible, int stableSlot) {
        if (pair == null || !pair.valid()) return null;
        if (leftFeasible && rightFeasible) {
            return Math.floorMod(stableSlot,2)==0 ? pair.left() : pair.right();
        }
        if (leftFeasible) return pair.left();
        if (rightFeasible) return pair.right();
        return null;
    }

    private static boolean finite(Vec2 v) {
        return v != null && Double.isFinite(v.x()) && Double.isFinite(v.z());
    }

    private SwarmSkeletonSightlinePolicy() {}
}
