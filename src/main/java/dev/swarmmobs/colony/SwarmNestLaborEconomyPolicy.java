package dev.swarmmobs.colony;

/**
 * One economy shared by room construction, food-limited births, worker
 * harvesting, Spider scouting and local pheromone response.
 *
 * Demand refers to actual, physically collected resource points. Targets
 * adapt to measured capacity pressure; this policy does not grant materials,
 * issue commands to remote workers or load chunks.
 */
public final class SwarmNestLaborEconomyPolicy {
    public record Targets(int soil, int timber, int food,
                          boolean canGrow, boolean chamberPressure) {}

    public static Targets targets(int localPopulation, int localCapacity,
                                  int chamberLevel, int hardCap) {
        int pop = Math.max(0, localPopulation);
        int cap = Math.max(1, localCapacity);
        boolean extendable = chamberLevel >= 0
                && chamberLevel < SwarmNestArchitecturePolicy.MAX_CHAMBER_LEVEL
                && cap < Math.max(1,hardCap);
        boolean crowded = pop >= Math.max(1,cap - 2);
        // Prior to a construction rush, store one module's real materials.
        // Near capacity, allow TWO cycles' buffer, never unbounded gathering.
        int soil = !extendable ? 0
                : SwarmNestArchitecturePolicy.SOIL_COST * (crowded ? 2 : 1);
        int timber = !extendable ? 0
                : SwarmNestArchitecturePolicy.TIMBER_COST * (crowded ? 2 : 1);
        // Reproduction consumes precisely one SPAWN_COST of actual nutrient
        // points per new member. A modest reserve prevents one-trip oscillation.
        int food = pop < cap
                ? 2 * SwarmNestColonyPolicy.SPAWN_COST
                : extendable ? SwarmNestColonyPolicy.SPAWN_COST : 0;
        return new Targets(soil,timber,food,extendable,crowded);
    }

    public static boolean needs(SwarmNestColonyPolicy.Kind kind,
                                int soil, int timber, int nutrition,
                                int totalStored, Targets targets) {
        if (targets == null || kind == null || kind == SwarmNestColonyPolicy.Kind.NONE
                || totalStored >= SwarmNestColonyPolicy.MAX_STORED_RESOURCES
                || SwarmNestColonyPolicy.acceptAmount(totalStored,1,kind)==0)
            return false;
        return switch (kind) {
            case SOIL -> soil < targets.soil();
            case TIMBER -> timber < targets.timber();
            case NUTRIENT -> nutrition < targets.food();
            case NONE -> false;
        };
    }

    public static double deficit(SwarmNestColonyPolicy.Kind kind,
                                 int soil, int timber, int food, Targets targets) {
        if (targets == null || kind == null) return 0.0;
        int required = switch (kind) {
            case SOIL -> targets.soil();
            case TIMBER -> targets.timber();
            case NUTRIENT -> targets.food();
            case NONE -> 0;
        };
        if (required <= 0) return 0.0;
        int current = switch (kind) {
            case SOIL -> soil;
            case TIMBER -> timber;
            case NUTRIENT -> food;
            case NONE -> 0;
        };
        return Math.max(0.0,Math.min(1.0,
                (required - Math.max(0,current))/(double)required));
    }

    private SwarmNestLaborEconomyPolicy() {}
}
