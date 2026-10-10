package dev.swarmmobs.colony;

/**
 * Resource-demand policy for physical colony work. No invisible production:
 * block mining and animal loot must enter the world as real dropped items.
 */
public final class SwarmColonyGatherPolicy {
    public static final int SOIL_TARGET = SwarmNestArchitecturePolicy.SOIL_COST;
    public static final int TIMBER_TARGET = SwarmNestArchitecturePolicy.TIMBER_COST;
    public static final int FOOD_TARGET = SwarmNestColonyPolicy.SPAWN_COST;
    public static final int TIMEOUT_TICKS = 200;
    public static final int STALL_TICKS = 85;
    public static final int RETRY_NAV_TICKS = 24;

    public static boolean needs(SwarmNestColonyPolicy.Kind kind,
                                int soil, int timber, int nutrition,
                                int stored) {
        if (stored >= SwarmNestColonyPolicy.MAX_STORED_RESOURCES) return false;
        return switch (kind == null ? SwarmNestColonyPolicy.Kind.NONE : kind) {
            case SOIL -> soil < SOIL_TARGET
                    && SwarmNestColonyPolicy.acceptAmount(stored, 1, kind) > 0;
            case TIMBER -> timber < TIMBER_TARGET
                    && SwarmNestColonyPolicy.acceptAmount(stored, 1, kind) > 0;
            case NUTRIENT -> nutrition < FOOD_TARGET
                    && SwarmNestColonyPolicy.acceptAmount(stored, 1, kind) > 0;
            case NONE -> false;
        };
    }

    /** Lower is preferred. Food gets strong priority while reproduction is hungry. */
    public static double score(SwarmNestColonyPolicy.Kind kind, double distanceSquared,
                               int soil, int timber, int nutrition) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0
                || kind == null || kind == SwarmNestColonyPolicy.Kind.NONE)
            return Double.POSITIVE_INFINITY;
        double deficit = switch (kind) {
            case SOIL -> fraction(soil, SOIL_TARGET);
            case TIMBER -> fraction(timber, TIMBER_TARGET);
            case NUTRIENT -> fraction(nutrition, FOOD_TARGET);
            case NONE -> 0.0;
        };
        return distanceSquared / (1.0 + 3.0 * deficit
                + (kind == SwarmNestColonyPolicy.Kind.NUTRIENT ? 2.0 : 0.0));
    }

    public static boolean expired(long now, long started, long progress) {
        return now < started || now - started >= TIMEOUT_TICKS
                || now - progress >= STALL_TICKS;
    }

    private static double fraction(int current, int target) {
        return Math.min(1.0, Math.max(0.0, (target - current) / (double) target));
    }

    private SwarmColonyGatherPolicy() {}
}
