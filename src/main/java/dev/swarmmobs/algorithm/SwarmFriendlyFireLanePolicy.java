package dev.swarmmobs.algorithm;

import java.util.List;

/**
 * Conservative two-dimensional friendly-fire corridor estimate for the
 * cross-species support plan. This never controls arrows or changes vanilla
 * targeting: it merely picks an out-of-line Skeleton destination.
 */
public final class SwarmFriendlyFireLanePolicy {
    public static final double CLEARANCE = 1.25;
    private static final double EPS = 1.0e-8;

    public static boolean isClear(SwarmCombatPlanner.Vec2 shooter,
                                  SwarmCombatPlanner.Vec2 target,
                                  List<SwarmCombatPlanner.Vec2> teammates) {
        if (shooter == null || target == null || teammates == null
                || !finite(shooter) || !finite(target)) return false;
        double dx = target.x() - shooter.x();
        double dz = target.z() - shooter.z();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < EPS) return false;
        for (SwarmCombatPlanner.Vec2 ally : teammates) {
            if (ally == null || !finite(ally)) continue;
            double px = ally.x() - shooter.x();
            double pz = ally.z() - shooter.z();
            double fraction = (px * dx + pz * dz) / lengthSquared;
            if (fraction <= 0.07 || fraction >= 0.92) continue;
            double deltaX = px - fraction * dx;
            double deltaZ = pz - fraction * dz;
            if (deltaX * deltaX + deltaZ * deltaZ < CLEARANCE * CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    /** Prefer geometrically safer side, with stable slot-based tie-breaks. */
    public static double chooseSide(int slot, boolean plusBlockClear,
                                    boolean minusBlockClear,
                                    boolean plusFriendlyClear,
                                    boolean minusFriendlyClear) {
        return SwarmFireSupportLanePolicy.choosePreferredSign(
                slot,plusBlockClear && plusFriendlyClear,
                minusBlockClear && minusFriendlyClear);
    }

    private static boolean finite(SwarmCombatPlanner.Vec2 v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.z());
    }

    private SwarmFriendlyFireLanePolicy() {}
}
