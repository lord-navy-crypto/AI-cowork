package dev.swarmmobs.algorithm;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Small direct-visibility hysteresis for multi-player swarms.
 *
 * The incumbent must remain a valid *currently visible* candidate and close
 * enough to the nearest visible player. A previously relayed/occluded player
 * never gets this preference.
 */
public final class SwarmVisibleTargetPolicy {
    // Switch away if a new player is at least 15% closer by real distance.
    public static final double INCUMBENT_DISTANCE_FACTOR = 1.15;

    public static <T> T choose(
            List<T> visible,
            UUID incumbentId,
            Function<T, UUID> id,
            ToDoubleFunction<T> squaredDistance
    ) {
        if (visible == null || visible.isEmpty()) {
            return null;
        }
        T closest = null;
        T incumbent = null;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        double incumbentDistanceSquared = Double.POSITIVE_INFINITY;
        for (T candidate : visible) {
            if (candidate == null) {
                continue;
            }
            double d2 = squaredDistance.applyAsDouble(candidate);
            if (!Double.isFinite(d2) || d2 < 0.0) {
                continue;
            }
            if (d2 < nearestDistanceSquared) {
                closest = candidate;
                nearestDistanceSquared = d2;
            }
            if (incumbentId != null && incumbentId.equals(id.apply(candidate))) {
                incumbent = candidate;
                incumbentDistanceSquared = d2;
            }
        }
        if (closest == null || incumbent == null) {
            return closest;
        }
        // Squared comparison avoids square roots in the hot selection path.
        double factor2 = INCUMBENT_DISTANCE_FACTOR * INCUMBENT_DISTANCE_FACTOR;
        return incumbentDistanceSquared <= nearestDistanceSquared * factor2
                ? incumbent : closest;
    }

    private SwarmVisibleTargetPolicy() {}
}
