package dev.swarmmobs.colony;

import dev.swarmmobs.agent.SwarmTaskType;
import dev.swarmmobs.algorithm.SwarmTaskBidPolicy;

import java.util.UUID;

/**
 * Bridges the EXISTING swarm division-of-labor response thresholds and
 * the colony's spatial signals. Individual thresholds are stable per UUID;
 * local scarcity, resource cues, stops and peer crowding change the bid.
 *
 * All coefficients are tunable gameplay model assumptions, NOT empirically
 * fitted rates of any particular ant or bee species.
 */
public final class SwarmColonyEmergencePolicy {
    public static final int MAX_CROWDING = 8;
    private static final double MAX_SCENT = 6.0;

    public static double shortage(SwarmNestColonyPolicy.Kind kind,
                                  int soil, int timber, int food) {
        if (kind == null) return 0;
        return switch (kind) {
            case SOIL -> deficit(soil, SwarmColonyGatherPolicy.SOIL_TARGET);
            case TIMBER -> deficit(timber, SwarmColonyGatherPolicy.TIMBER_TARGET);
            case NUTRIENT -> deficit(food, SwarmColonyGatherPolicy.FOOD_TARGET);
            case NONE -> 0.0;
        };
    }

    private static double deficit(int stock, int target) {
        return Math.max(0, Math.min(1,
                (target - Math.max(0, stock)) / (double)Math.max(1,target)));
    }

    /**
     * Physically valid candidate selection:
     * lower score => higher priority. Caller must first validate the resource,
     * colony stock capacity, combat idle state and unloaded-chunk constraints.
     *
     * s = 0.12 + 0.8 shortage + 0.09 local scent
     * p = s² / (s² + theta_i²) (reuse the established insect response rule)
     * C = baseCost * (1+.28 crowd) * (1+.3 stop)
     *             / ((.55+.65p)(1+.16 scent)(1+.10 expertise))
     */
    public static double workCost(double baseCost, UUID worker,
                                  SwarmNestColonyPolicy.Kind kind,
                                  int soil, int timber, int food,
                                  double scent, double stop,
                                  int nearbyWorkers, double experience) {
        if (worker == null || kind == null
                || kind == SwarmNestColonyPolicy.Kind.NONE
                || !Double.isFinite(baseCost) || baseCost < 0)
            return Double.POSITIVE_INFINITY;
        double need = shortage(kind,soil,timber,food);
        double attraction = bounded(scent,0,MAX_SCENT);
        double repellent = bounded(stop,0,MAX_SCENT);
        SwarmTaskType originalSwarmTask = kind == SwarmNestColonyPolicy.Kind.NUTRIENT
                ? SwarmTaskType.MATERIAL : SwarmTaskType.ENGINEERING;
        double theta = SwarmTaskBidPolicy.responseThreshold(
                worker,originalSwarmTask,1.0);
        double stimulus = 0.12 + 0.8 * need + 0.09 * attraction;
        double response = SwarmColonySciencePolicy.response(stimulus, theta);
        double crowd = Math.min(MAX_CROWDING,Math.max(0,nearbyWorkers));
        double skill = bounded(experience,0,1);
        double multiplier = (1.0+0.28*crowd) * (1.0+0.30*repellent)
                / ((0.55+0.65*response)*(1.0+0.16*attraction)
                    *(1.0+0.10*skill));
        return baseCost * bounded(multiplier,0.50,3.5);
    }

    /**
     * The previously implemented pheromone return loop must NOT reinforce
     * crowded routes as strongly as empty routes. Evaluated at the physical
     * delivery event, not on every tick.
     */
    public static double depositionMultiplier(int nearbyWorkers) {
        int crowd = Math.min(MAX_CROWDING,Math.max(0,nearbyWorkers));
        return 1.0 / (1.0+0.55*crowd);
    }

    /** Permanent mild scout heterogeneity avoids all workers mirroring trails. */
    public static boolean isIndependentExplorer(UUID worker) {
        if (worker == null) return false;
        long h = worker.getMostSignificantBits()
                ^ Long.rotateLeft(worker.getLeastSignificantBits(),13);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h & 7L) == 0L;
    }

    /** Independent explorers still read warning marks, but follow scent less. */
    public static double sensedAttraction(UUID worker, double scent) {
        double raw = bounded(scent,0,MAX_SCENT);
        return isIndependentExplorer(worker) ? 0.35*raw : raw;
    }

    private static double bounded(double value,double min,double max) {
        return !Double.isFinite(value) ? min : Math.max(min,Math.min(max,value));
    }

    private SwarmColonyEmergencePolicy() {}
}
