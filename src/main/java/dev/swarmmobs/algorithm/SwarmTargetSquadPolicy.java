package dev.swarmmobs.algorithm;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * A tactical squad is the subset of locally sensed allies pursuing the same
 * known target. Physical collision avoidance and message relay must continue
 * to consider all nearby allies; ONLY tactical assignment is target-scoped.
 *
 * Peers without known target IDs are excluded until they discover or receive
 * a real observation. In particular null never means "same target".
 */
public final class SwarmTargetSquadPolicy {
    public static <T> List<T> sameTarget(
            UUID targetId,
            List<T> neighbors,
            Function<T, UUID> targetIdForPeer
    ) {
        if (targetId == null || neighbors == null || neighbors.isEmpty()) {
            return List.of();
        }
        return neighbors.stream()
                .filter(peer -> peer != null
                        && targetId.equals(targetIdForPeer.apply(peer)))
                .toList();
    }

    private SwarmTargetSquadPolicy() {}
}
