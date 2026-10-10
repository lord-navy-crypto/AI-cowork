package dev.swarmmobs.algorithm;

/**
 * Fixed-capacity, per-agent memory of failed Minecraft navigation waypoints.
 * Only game-world X/Z positions and game ticks are stored; no player positions,
 * world queries, pathfinding, allocations during evaluation or remote state.
 */
public final class SwarmFailedWaypointMemory {
    public static final int CAPACITY = 3;

    private final double[] x = new double[CAPACITY];
    private final double[] z = new double[CAPACITY];
    private final long[] expires = new long[CAPACITY];
    private int nextSlot;

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) expires[i] = 0L;
        nextSlot = 0;
    }

    /** Replace an existing nearby failure; otherwise evict the oldest slot. */
    public void record(double px, double pz, long tick, long ttlTicks,
            double mergeDistance) {
        if (!Double.isFinite(px) || !Double.isFinite(pz) || tick < 0
                || ttlTicks <= 0 || !Double.isFinite(mergeDistance)
                || mergeDistance < 0) return;
        long expiry = SwarmGameHandoffTimeoutPolicy.nextEligibleTick(tick, ttlTicks);
        int slot = -1;
        for (int i = 0; i < CAPACITY; i++) {
            if (expires[i] > tick && Math.hypot(px-x[i],pz-z[i]) < mergeDistance) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            // Reuse expired capacity before evicting a still-active failure.
            for (int i = 0; i < CAPACITY; i++) {
                if (expires[i] <= tick) {
                    slot = i;
                    break;
                }
            }
        }
        if (slot < 0) {
            slot = nextSlot;
        }
        x[slot] = px;
        z[slot] = pz;
        expires[slot] = expiry;
        nextSlot = (slot + 1) % CAPACITY;
    }

    public boolean allows(double px, double pz, long tick,
            double exclusionRadius) {
        if (!Double.isFinite(px) || !Double.isFinite(pz) || tick < 0
                || !Double.isFinite(exclusionRadius) || exclusionRadius < 0) {
            return false;
        }
        for (int i = 0; i < CAPACITY; i++) {
            if (expires[i] > tick
                    && Math.hypot(px-x[i],pz-z[i]) < exclusionRadius) {
                return false;
            }
        }
        return true;
    }

    public boolean hasActive(long tick) {
        if (tick < 0) return false;
        for (long expiry : expires) {
            if (expiry > tick) return true;
        }
        return false;
    }

    public int activeCount(long tick) {
        if (tick < 0) return 0;
        int count = 0;
        for (long expiry : expires) if (expiry > tick) count++;
        return count;
    }
}
