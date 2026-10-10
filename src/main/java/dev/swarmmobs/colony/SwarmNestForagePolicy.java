package dev.swarmmobs.colony;

/**
 * Pure guards for a deliberately narrow, opt-in renewable food harvest.
 * A berry bush remains planted; only ripe berries become physical drops.
 * No terrain digging, log cutting, hidden inventory or remote storage.
 */
public final class SwarmNestForagePolicy {
    public static final int WORK_TIMEOUT_TICKS = 140;
    public static final int RETRY_MOVE_TICKS = 24;
    public static final int STALL_TICKS = 80;
    public static final int PLAYER_PROTECTION_RADIUS = 16;

    /** Minecraft ripe sweet berry bushes are age 2 or 3. */
    public static int yieldForAge(int age) {
        return switch (age) {
            case 2 -> 1;
            case 3 -> 2;
            default -> 0;
        };
    }

    public static boolean mayHarvest(boolean enabled, boolean hauling,
                                     boolean idle, boolean coreLoaded,
                                     boolean playerClose, int age,
                                     int storedPoints, int availableNutrition) {
        return enabled && hauling && idle && coreLoaded && !playerClose
                && availableNutrition < SwarmNestColonyPolicy.SPAWN_COST
                && SwarmNestColonyPolicy.acceptAmount(storedPoints,
                        yieldForAge(age), SwarmNestColonyPolicy.Kind.NUTRIENT) > 0;
    }

    public static boolean expired(long currentTick, long startedTick,
                                  long lastProgressTick) {
        return currentTick < startedTick
                || currentTick - startedTick >= WORK_TIMEOUT_TICKS
                || currentTick - lastProgressTick >= STALL_TICKS;
    }

    private SwarmNestForagePolicy() {}
}
