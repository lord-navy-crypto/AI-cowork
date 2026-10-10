package dev.swarmmobs.colony;

/**
 * Small above-ground masonry/timber footprint inspired by local deposition in
 * ant nests, NOT a literal honeycomb or underground excavation.
 *
 * Two shell elements visually represent each completed chamber module.
 * The first four chambers build eight positions at ground level, while
 * later chambers extend the same footprint upward only over owned shell.
 */
public final class SwarmNestVisibleShellPolicy {
    private static final int[] X = {2, -2, 0, 0, 2, -2, 2, -2};
    private static final int[] Z = {0, 0, 2, -2, 2, 2, -2, -2};

    public record Piece(int x, int y, int z, boolean soil) {}

    public static Piece piece(int chamber, int slot) {
        if (chamber < 0 || chamber >= SwarmNestArchitecturePolicy.MAX_CHAMBER_LEVEL
                || slot < 0 || slot > 1) {
            throw new IllegalArgumentException("Invalid nest shell chamber or slot");
        }
        int index = chamber * 2 + slot;
        return new Piece(X[index % X.length], index / X.length, Z[index % Z.length],
                slot == 0);
    }

    private SwarmNestVisibleShellPolicy() {}
}
