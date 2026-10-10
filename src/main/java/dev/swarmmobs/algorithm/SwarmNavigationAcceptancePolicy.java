package dev.swarmmobs.algorithm;

/**
 * Backoff after Minecraft's PathNavigation.moveTo(...) rejects a command.
 *
 * A rejected command is NOT an active path and must not supply progress
 * evidence. A different destination may retry promptly; an unchanged
 * impossible destination waits a bounded number of game ticks.
 */
public final class SwarmNavigationAcceptancePolicy {
    public static final int SAME_DESTINATION_RETRY_TICKS = 12;
    public static final double DESTINATION_CHANGE_BLOCKS = 0.5;

    public static boolean mayRetry(long now, long rejectedAt,
            double rejectedX, double rejectedY, double rejectedZ,
            double nextX, double nextY, double nextZ) {
        if (!finite(nextX, nextY, nextZ)) return false;
        if (rejectedAt == Long.MIN_VALUE || now < rejectedAt) return true;
        if (!finite(rejectedX, rejectedY, rejectedZ)) return true;
        if (Math.hypot(nextX - rejectedX, nextZ - rejectedZ)
                >= DESTINATION_CHANGE_BLOCKS
                || Math.abs(nextY - rejectedY) >= DESTINATION_CHANGE_BLOCKS) {
            return true;
        }
        return now - rejectedAt >= SAME_DESTINATION_RETRY_TICKS;
    }

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    private SwarmNavigationAcceptancePolicy() {}
}
