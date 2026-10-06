package dev.swarmmobs.algorithm;

/**
 * Bounded interpretation of Minecraft PathNavigation evidence.
 *
 * A non-null path that cannot reach the exact target block may still be useful
 * for short-horizon local planning when it terminates within a small residual
 * distance. Null paths remain unusable.
 */
public final class SwarmPathEvidencePolicy {

    public static boolean acceptable(
            boolean pathExists,
            boolean canReachExactly,
            double residualDistance,
            double maxResidualDistance
    ) {
        if (!pathExists) {
            return false;
        }

        if (canReachExactly) {
            return true;
        }

        if (!Double.isFinite(residualDistance)) {
            return false;
        }

        return Math.max(0.0, residualDistance)
                <= Math.max(0.0, maxResidualDistance);
    }

    private SwarmPathEvidencePolicy() {}
}
