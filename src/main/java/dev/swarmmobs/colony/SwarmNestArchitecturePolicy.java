package dev.swarmmobs.colony;

/**
 * Abstract nest-chamber modules: construction consumes real soil/timber
 * reserves but does not excavate or replace any player-world blocks.
 */
public final class SwarmNestArchitecturePolicy {
    public static final int BASE_CAPACITY = 4;
    public static final int CAPACITY_PER_CHAMBER = 4;
    public static final int MAX_CHAMBER_LEVEL = 7;
    public static final int SOIL_COST = 8;
    public static final int TIMBER_COST = 6;

    public static int effectiveCapacity(int chamberLevel, int configuredHardCap) {
        int hardCap = Math.max(1, configuredHardCap);
        int chambers = Math.max(0, Math.min(MAX_CHAMBER_LEVEL, chamberLevel));
        return Math.min(hardCap, BASE_CAPACITY + CAPACITY_PER_CHAMBER * chambers);
    }

    public static boolean canExtend(int chamberLevel, int hardCap,
                                    int localPopulation, int soilPoints,
                                    int timberPoints) {
        int now = effectiveCapacity(chamberLevel, hardCap);
        return chamberLevel >= 0 && chamberLevel < MAX_CHAMBER_LEVEL
                && now < Math.max(1, hardCap)
                && localPopulation >= Math.max(2, now - 1)
                && soilPoints >= SOIL_COST && timberPoints >= TIMBER_COST;
    }

    private SwarmNestArchitecturePolicy() {}
}
