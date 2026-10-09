package dev.swarmmobs.colony;

/** Cheap, deterministic item-hauling safety and progress rules. */
public final class SwarmNestHaulPolicy {
    public static final int MAX_ACTIVE_TICKS = 260;
    public static final int RETRY_NAV_TICKS = 24;
    public static final int LEASE_TICKS = 60;

    public static boolean eligible(boolean enabled, boolean workerIdle,
                                   boolean homeLoaded, boolean itemAllowed,
                                   boolean playerNearItem, int count,
                                   int maxStackCount, int remainingCapacity) {
        return enabled && workerIdle && homeLoaded && itemAllowed
                && !playerNearItem && count > 0
                && count <= Math.max(1, maxStackCount)
                && remainingCapacity > 0;
    }

    public static boolean mayClaim(String previousOwner, long expiry,
                                   String workerId, long tick) {
        return previousOwner == null || previousOwner.isEmpty()
                || previousOwner.equals(workerId) || expiry < tick;
    }

    public static boolean canContinue(long elapsed, boolean workerIdle,
                                      boolean itemAlive, boolean nestValid) {
        return elapsed < MAX_ACTIVE_TICKS && workerIdle && itemAlive && nestValid;
    }

    private SwarmNestHaulPolicy() {}
}
