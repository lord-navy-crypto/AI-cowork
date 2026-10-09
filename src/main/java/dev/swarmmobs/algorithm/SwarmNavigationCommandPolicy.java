package dev.swarmmobs.algorithm;

/**
 * Suppresses redundant PathNavigation.moveTo calls while preserving bounded
 * retries when navigation has finished or the world changes around a mob.
 *
 * This policy is only for issuing movement commands; obstacle/recovery
 * planning and the vanilla combat handoff still run normally.
 */
public final class SwarmNavigationCommandPolicy {
    public static final int DONE_RETRY_TICKS = 8;
    public static final int ACTIVE_REFRESH_TICKS = 20;

    // Movement target changes greater than a quarter block invalidate the
    // current path. Minor oscillations are tolerated to avoid path thrashing.
    private static final double TARGET_CHANGE_SQUARED = 0.25 * 0.25;
    private static final double VERTICAL_CHANGE = 0.5;
    private static final double SPEED_CHANGE = 0.05;

    public enum Decision {
        INITIAL, TARGET_CHANGED, SPEED_CHANGED, RETRY_DONE, REFRESH_ACTIVE, SKIP
    }

    public static Decision evaluate(
            boolean commandIssued,
            boolean navigationDone,
            long tick,
            long lastCommandTick,
            double x,
            double y,
            double z,
            double speed,
            double lastX,
            double lastY,
            double lastZ,
            double lastSpeed
    ) {
        if (!commandIssued || tick < lastCommandTick) {
            return Decision.INITIAL;
        }
        double dx = x - lastX;
        double dz = z - lastZ;
        if (dx * dx + dz * dz > TARGET_CHANGE_SQUARED
                || Math.abs(y - lastY) > VERTICAL_CHANGE) {
            return Decision.TARGET_CHANGED;
        }
        if (Math.abs(speed - lastSpeed) > SPEED_CHANGE) {
            return Decision.SPEED_CHANGED;
        }
        long elapsed = tick - lastCommandTick;
        if (navigationDone && elapsed >= DONE_RETRY_TICKS) {
            return Decision.RETRY_DONE;
        }
        if (!navigationDone && elapsed >= ACTIVE_REFRESH_TICKS) {
            return Decision.REFRESH_ACTIVE;
        }
        return Decision.SKIP;
    }

    private SwarmNavigationCommandPolicy() {}
}
