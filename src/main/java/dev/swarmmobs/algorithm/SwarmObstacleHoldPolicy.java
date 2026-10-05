package dev.swarmmobs.algorithm;

/**
 * Small deterministic hysteresis policy for temporary obstacle-avoidance detours.
 * Keeps an active detour long enough to avoid rapid flip-flopping near obstacle edges,
 * but releases it early once the waypoint is reached.
 */
public final class SwarmObstacleHoldPolicy {

    public static boolean shouldKeepDetour(
            long currentTick,
            long holdUntilTick,
            double distanceToWaypoint,
            double arrivalTolerance
    ) {
        if (currentTick >= holdUntilTick) {
            return false;
        }

        double tolerance = Math.max(0.0, arrivalTolerance);
        return distanceToWaypoint > tolerance;
    }

    public static long holdUntil(long currentTick, int holdTicks) {
        return currentTick + Math.max(0, holdTicks);
    }

    private SwarmObstacleHoldPolicy() {}
}
