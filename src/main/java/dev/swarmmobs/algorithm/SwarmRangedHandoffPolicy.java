package dev.swarmmobs.algorithm;

/**
 * Handoff policy between swarm repositioning and vanilla ranged combat.
 *
 * Ranged support should keep using the swarm movement planner while it is
 * outside effective bow range or lacks a direct, valid view of the same
 * target. Once a direct target is in ranged-combat distance, the swarm MOVE
 * goal yields so vanilla RangedBowAttackGoal can own movement/aim/fire.
 */
public final class SwarmRangedHandoffPolicy {

    public static boolean shouldYieldToVanilla(
            boolean directObservation,
            boolean sameTarget,
            boolean hasLineOfSight,
            double distanceSqr,
            double handoffDistance
    ) {
        if (!directObservation || !sameTarget || !hasLineOfSight) {
            return false;
        }

        double boundedDistance = Math.max(0.0, handoffDistance);
        return distanceSqr <= boundedDistance * boundedDistance;
    }

    private SwarmRangedHandoffPolicy() {}
}
