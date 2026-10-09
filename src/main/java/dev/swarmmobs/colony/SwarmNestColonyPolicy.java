package dev.swarmmobs.colony;

/** Pure and deterministic guard rails for colony resource accounting. */
public final class SwarmNestColonyPolicy {
    public static final int MAX_STORED_RESOURCES = 128;
    public static final int SPAWN_COST = 12;
    public static final int SPAWN_COOLDOWN_TICKS = 1200;

    public enum Kind { SOIL, TIMBER, NUTRIENT, NONE }

    public static int value(Kind kind) {
        return switch (kind == null ? Kind.NONE : kind) {
            case SOIL -> 1;
            case TIMBER -> 3;
            case NUTRIENT -> 4;
            case NONE -> 0;
        };
    }

    public static int acceptAmount(int current, int itemCount, Kind kind) {
        int unit = value(kind);
        if (unit == 0 || itemCount <= 0 || current >= MAX_STORED_RESOURCES) return 0;
        return Math.min(itemCount, (MAX_STORED_RESOURCES - Math.max(0, current)) / unit);
    }

    public static boolean canSpawn(
            boolean enabled, boolean mobSpawningEnabled,
            boolean difficultyAllowsMonsters, boolean gameLoaded,
            boolean playerInSimulationRange, boolean nearbyPlayerTooClose,
            boolean validPlacement, int storedResources, int population,
            int maxPopulation, long now, long nextSpawnTick
    ) {
        return enabled && mobSpawningEnabled && difficultyAllowsMonsters
                && gameLoaded && playerInSimulationRange && !nearbyPlayerTooClose
                && validPlacement && storedResources >= SPAWN_COST
                && population < Math.max(1, maxPopulation)
                && now >= nextSpawnTick;
    }

    private SwarmNestColonyPolicy() {}
}
