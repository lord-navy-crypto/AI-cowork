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

    /**
     * A loaded core must be able to accept at least one WHOLE item at its
     * category's point value. A nearly full nest cannot accept a 3-point
     * log or 4-point food item merely because one resource point remains.
     */
    public static boolean hasRoomFor(int storedPoints, int stackSize,
                                     SwarmNestColonyPolicy.Kind kind) {
        return SwarmNestColonyPolicy.acceptAmount(storedPoints, stackSize, kind) > 0;
    }

    /**
     * Local response-threshold-style pickup priority. Distances remain the
     * primary cost, but a shortage of an expansion material or food lowers
     * that cost. No global inventory scan, invented resources or task magic.
     * An impossible chamber upgrade has no construction shortage.
     */
    public static double pickupScore(SwarmNestColonyPolicy.Kind kind,
                                     double distanceSquared,
                                     int soilPoints, int timberPoints,
                                     int nutrientPoints, int legacyPoints,
                                     int chamberLevel, int configuredCap) {
        if (kind == null || kind == SwarmNestColonyPolicy.Kind.NONE
                || !Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        boolean mayExpand = chamberLevel < SwarmNestArchitecturePolicy.MAX_CHAMBER_LEVEL
                && SwarmNestArchitecturePolicy.effectiveCapacity(chamberLevel, configuredCap)
                        < Math.max(1, configuredCap);
        double shortage = switch (kind) {
            case SOIL -> mayExpand
                    ? deficit(soilPoints, SwarmNestArchitecturePolicy.SOIL_COST) : 0.0;
            case TIMBER -> mayExpand
                    ? deficit(timberPoints, SwarmNestArchitecturePolicy.TIMBER_COST) : 0.0;
            case NUTRIENT -> deficit(Math.max(0, nutrientPoints)
                            + Math.max(0, legacyPoints), SwarmNestColonyPolicy.SPAWN_COST);
            case NONE -> 0.0;
        };
        return distanceSquared / (1.0 + 1.5 * shortage);
    }

    private static double deficit(int available, int target) {
        return Math.max(0.0, (target - Math.max(0, available)) / (double) target);
    }

    // A worker that stops making material progress releases its real-item
    // lease instead of occupying an unreachable delivery task indefinitely.
    public static final int STALL_TICKS = 100;
    public static boolean progress(double bestDistanceSquared, double currentDistanceSquared) {
        return Double.isFinite(currentDistanceSquared)
                && currentDistanceSquared >= 0.0
                && (Double.isInfinite(bestDistanceSquared)
                        || currentDistanceSquared + 0.5 < bestDistanceSquared);
    }

    public static boolean stalled(long tick, long lastProgressTick) {
        return tick >= lastProgressTick && tick - lastProgressTick >= STALL_TICKS;
    }

    private SwarmNestHaulPolicy() {}
}
