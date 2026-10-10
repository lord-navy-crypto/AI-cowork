package dev.swarmmobs.algorithm;

/**
 * Pure preflight for the opt-in idle Nest Core construction feature. World
 * checks (loaded chunk, block state and nearby players) stay server-side.
 */
public final class SwarmNestSitePolicy {
    public static boolean eligible(
            boolean featureEnabled,
            boolean mobGriefing,
            boolean hasTarget,
            boolean hasDestination,
            boolean nearbyPlayer,
            int localPopulation,
            int minimumPopulation,
            boolean availableSite,
            boolean nestAlreadyNearby
    ) {
        return featureEnabled
                && mobGriefing
                && !hasTarget
                && !hasDestination
                && !nearbyPlayer
                && localPopulation >= Math.max(2, minimumPopulation)
                && availableSite
                && !nestAlreadyNearby;
    }

    public static boolean naturalFoundation(
            boolean hasNaturalSoil,
            boolean placementIsAir,
            boolean spaceAboveIsAir,
            boolean hasFluid
    ) {
        return hasNaturalSoil && placementIsAir
                && spaceAboveIsAir && !hasFluid;
    }

    private SwarmNestSitePolicy() {}
}
