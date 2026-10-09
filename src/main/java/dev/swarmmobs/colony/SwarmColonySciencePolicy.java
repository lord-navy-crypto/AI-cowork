package dev.swarmmobs.colony;

/**
 * Game-scale adaptation of insect response-threshold task allocation and
 * food-limited population modelling. Numbers are TUNABLE GAME PARAMETERS,
 * not biological measurements or claims about real ant/bee colony rates.
 *
 * Colony size is a LOCAL loaded-region count, not a dimension-wide census.
 */
public final class SwarmColonySciencePolicy {
    public enum Job { WORKER, GUARD, SCOUT, RESERVE }

    public record Sample(
            int population, int capacity,
            int workers, int guards, int scouts, int reserves,
            double occupancy, double nutritionReadiness,
            double workerResponse, double guardResponse,
            double scoutResponse, double reserveResponse,
            Job recommendedRecruit
    ) {
        public double response(Job job) {
            return switch (job) {
                case WORKER -> workerResponse;
                case GUARD -> guardResponse;
                case SCOUT -> scoutResponse;
                case RESERVE -> reserveResponse;
            };
        }
    }

    /** s² / (s² + theta²): deterministic, monotone stimulus-response index. */
    public static double response(double stimulus, double threshold) {
        double s = clamp(stimulus, 0.0, 1000.0);
        double theta = clamp(threshold, 0.01, 1000.0);
        return s * s / (s * s + theta * theta);
    }

    public static Sample evaluate(
            int zombies, int skeletons, int spiders, int creepers,
            int capacity, int nutrientPoints,
            double workerTargetShare, double guardTargetShare,
            double responseThreshold
    ) {
        int workers = Math.max(0, zombies);
        int guards = Math.max(0, skeletons);
        int scouts = Math.max(0, spiders);
        int reserves = Math.max(0, creepers);
        int population = workers + guards + scouts + reserves;
        int limit = Math.max(1, capacity);
        double workerShare = clamp(workerTargetShare, 0.15, 0.65);
        double guardShare = clamp(guardTargetShare, 0.10, 0.50);
        // User-editable targets cannot eliminate scouts and reserves by
        // accidentally summing above 100%. Preserve at least 20% combined.
        double combined = workerShare + guardShare;
        if (combined > 0.80) {
            double scale = 0.80 / combined;
            workerShare *= scale;
            guardShare *= scale;
        }
        double remaining = Math.max(0.0, 1.0 - workerShare - guardShare);
        double scoutShare = remaining * 0.60;
        double reserveShare = remaining - scoutShare;
        double theta = clamp(responseThreshold, 0.10, 3.0);

        // Deficits project one recruit ahead, instead of waiting until the
        // colony has already overshot the desired composition.
        double next = population + 1.0;
        double w = response(Math.max(0, workerShare * next - workers), theta * 0.9);
        double g = response(Math.max(0, guardShare * next - guards), theta);
        double s = response(Math.max(0, scoutShare * next - scouts), theta * 1.1);
        double r = response(Math.max(0, reserveShare * next - reserves), theta * 1.2);

        Job recruit = Job.WORKER;
        double highest = w;
        if (g > highest) { recruit = Job.GUARD; highest = g; }
        if (s > highest) { recruit = Job.SCOUT; highest = s; }
        if (r > highest) { recruit = Job.RESERVE; }

        return new Sample(population, limit, workers, guards, scouts, reserves,
                Math.min(1.0, (double) population / limit),
                Math.min(1.0, Math.max(0, nutrientPoints)
                        / (double) SwarmNestColonyPolicy.SPAWN_COST),
                w, g, s, r, recruit);
    }

    private static double clamp(double value, double lo, double hi) {
        return !Double.isFinite(value) ? lo : Math.max(lo, Math.min(hi, value));
    }

    private SwarmColonySciencePolicy() {}
}
