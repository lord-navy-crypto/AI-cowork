package dev.swarmmobs.colony;

/**
 * Stable nest ownership for the loaded local population census.
 *
 * Distance alone is insufficient: two neighboring nests must not count
 * the same already-enrolled workers as their own capacity, labor demand,
 * or birth eligibility. A lost home can be replaced only when its chunk
 * is loaded and the previous Nest Core is confirmed absent.
 */
public final class SwarmNestMembershipPolicy {
    public static boolean eligible(boolean hasHome, boolean sameDimension,
                                   boolean sameHome,
                                   boolean foreignHomeLoaded,
                                   boolean foreignHomeStillValid) {
        if (!hasHome || !sameDimension || sameHome) return true;
        if (!foreignHomeLoaded) return false; // Never infer absence from unloaded chunks.
        return !foreignHomeStillValid;
    }

    private SwarmNestMembershipPolicy() {}
}
