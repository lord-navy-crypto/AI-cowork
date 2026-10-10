package dev.swarmmobs.algorithm;

/**
 * Local Minecraft NPC optional-movement progress watchdog.
 *
 * A waypoint being nearby and standable does not prove that a navigation
 * path can reach it. Evaluate actual distance improvement at fixed game
 * tick intervals without querying the world, opponents, or a pathfinder.
 */
public final class SwarmOptionalWaypointProgressPolicy {
    public static final long FIRST_CHECK_TICKS = 12L;
    public static final long PATH_FINISHED_GRACE_TICKS = 6L;
    public static final double MIN_IMPROVEMENT_BLOCKS = 0.25;
    public static final double ARRIVAL_BLOCKS = 1.2;
    public static final double WAYPOINT_CHANGE_BLOCKS = 1.5;

    public enum Decision { RESET, WAIT, PROGRESS, ARRIVED, STALLED }
    // Larger than the command policy's 0.25-block replan threshold to
    // allow tiny floating-point differences, but never an old destination.
    public static final double MATCHED_PATH_TOLERANCE = 0.35;

    /**
     * A measured stall is evidence about the command that Minecraft
     * navigation actually received, not merely a newly planned waypoint.
     * The planner can change its target while path-budget scheduling
     * intentionally leaves an older command in control.
     */
    public static boolean matchesCommand(boolean commandIssued,
            double plannedX, double plannedZ,
            double commandedX, double commandedZ) {
        return commandIssued && Double.isFinite(plannedX)
                && Double.isFinite(plannedZ) && Double.isFinite(commandedX)
                && Double.isFinite(commandedZ)
                && Math.hypot(plannedX - commandedX, plannedZ - commandedZ)
                        <= MATCHED_PATH_TOLERANCE;
    }


    public static Decision assess(long tick, long sampleTick,
            double sampleDistance, double remainingDistance,
            double waypointShift, boolean navigationDone) {
        if (tick < 0 || sampleTick < 0 || tick < sampleTick
                || !Double.isFinite(sampleDistance)
                || !Double.isFinite(remainingDistance)
                || !Double.isFinite(waypointShift)
                || sampleDistance < 0 || remainingDistance < 0
                || waypointShift < 0 || waypointShift >= WAYPOINT_CHANGE_BLOCKS) {
            return Decision.RESET;
        }
        if (remainingDistance <= ARRIVAL_BLOCKS) return Decision.ARRIVED;
        long elapsed = tick - sampleTick;
        if (elapsed < PATH_FINISHED_GRACE_TICKS) return Decision.WAIT;
        if (navigationDone) return Decision.STALLED;
        if (elapsed < FIRST_CHECK_TICKS) return Decision.WAIT;
        return sampleDistance - remainingDistance >= MIN_IMPROVEMENT_BLOCKS
                ? Decision.PROGRESS : Decision.STALLED;
    }

    private SwarmOptionalWaypointProgressPolicy() {}
}
