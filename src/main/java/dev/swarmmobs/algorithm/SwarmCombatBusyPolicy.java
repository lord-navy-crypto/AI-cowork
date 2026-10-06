package dev.swarmmobs.algorithm;

/**
 * Shared close-combat opportunity gate used by labor bidding and engineering.
 * Distance alone is insufficient: a wall-blocked target is not an active melee
 * opportunity and must not suppress recovery/engineering work.
 */
public final class SwarmCombatBusyPolicy {
    public static boolean isBusy(
            boolean hasTarget,
            boolean hasLineOfSight,
            double distanceSqr,
            double releaseDistance
    ) {
        if (!hasTarget || !hasLineOfSight || !Double.isFinite(distanceSqr)) {
            return false;
        }

        double release = Math.max(0.0, releaseDistance);
        return distanceSqr <= release * release;
    }

    private SwarmCombatBusyPolicy() {}
}
