package dev.swarmmobs.algorithm;

/**
 * Bounded deterministic policy for bare-handed Zombie engineering.
 *
 * The timing model approximates vanilla hand-breaking behavior:
 * blocks that do not require a correct harvest tool use the faster 30x hardness
 * denominator, while blocks that require a correct tool use the slower 100x path.
 */
public final class SwarmZombieEngineeringPolicy {

    public static boolean canAttemptBreak(
            double hardness,
            double maxHardness,
            boolean hasBlockEntity
    ) {
        return Double.isFinite(hardness)
                && hardness >= 0.0
                && hardness <= Math.max(0.0, maxHardness)
                && !hasBlockEntity;
    }

    public static int handBreakTicks(
            double hardness,
            boolean requiresCorrectToolForDrops
    ) {
        if (!Double.isFinite(hardness) || hardness < 0.0) {
            return Integer.MAX_VALUE;
        }

        double divisor = requiresCorrectToolForDrops ? 100.0 : 30.0;
        return Math.max(1, (int) Math.ceil(hardness * divisor));
    }

    public static boolean canSalvageAsBuildingMaterial(
            boolean requiresCorrectToolForDrops,
            boolean hasBlockItem,
            boolean hasCollision
    ) {
        return !requiresCorrectToolForDrops && hasBlockItem && hasCollision;
    }

    private SwarmZombieEngineeringPolicy() {}
}
