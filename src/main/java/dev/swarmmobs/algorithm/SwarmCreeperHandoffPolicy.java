package dev.swarmmobs.algorithm;

/**
 * Handoff policy between swarm repositioning and vanilla Creeper fuse logic.
 */
public final class SwarmCreeperHandoffPolicy {

    public static boolean shouldYieldToSwell(
            boolean directObservation,
            boolean sameTarget,
            boolean hasLineOfSight,
            double distanceSqr,
            double handoffDistance,
            int swellDirection,
            boolean ignited
    ) {
        if (ignited || swellDirection > 0) {
            return true;
        }

        if (!directObservation || !sameTarget || !hasLineOfSight) {
            return false;
        }

        double boundedDistance = Math.max(0.0, handoffDistance);
        return distanceSqr <= boundedDistance * boundedDistance;
    }

    private SwarmCreeperHandoffPolicy() {}
}
