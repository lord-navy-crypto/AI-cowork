package dev.swarmmobs.algorithm;

/**
 * Deterministic helper for classifying whether a local navigation probe has usable
 * ground support within an allowed drop depth.
 */
public final class SwarmTerrainSupportPolicy {

    public static boolean hasSupport(boolean[] supportByDropDepth, int maxDropBlocks) {
        if (supportByDropDepth == null || supportByDropDepth.length == 0) {
            return false;
        }

        int maxDepth = Math.max(0, Math.min(maxDropBlocks, supportByDropDepth.length - 1));
        for (int depth = 0; depth <= maxDepth; depth++) {
            if (supportByDropDepth[depth]) {
                return true;
            }
        }

        return false;
    }

    private SwarmTerrainSupportPolicy() {}
}
