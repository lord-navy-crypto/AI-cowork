package dev.swarmmobs.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Selects movement and communication peers from one sorted neighborhood scan.
 *
 * Each channel applies its own radius and neighbor cap, preserving the old
 * independent nearest-neighbor behavior without querying the entity index twice.
 */
public final class SwarmNeighborSelectionPolicy {
    public record Selection<T>(List<T> movement, List<T> communication) {}

    public static <T> Selection<T> select(
            List<T> candidates,
            ToDoubleFunction<T> distanceSquared,
            double movementRadius,
            double communicationRadius,
            int maxNeighbors,
            boolean communicationEnabled
    ) {
        int cap = Math.max(0, maxNeighbors);
        if (cap == 0 || candidates.isEmpty()) {
            return new Selection<>(List.of(), List.of());
        }

        List<T> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(distanceSquared));

        double movementRangeSquared = Math.max(0.0, movementRadius) * Math.max(0.0, movementRadius);
        double communicationRangeSquared = Math.max(0.0, communicationRadius) * Math.max(0.0, communicationRadius);
        List<T> movement = new ArrayList<>(Math.min(cap, ordered.size()));
        List<T> communication = communicationEnabled
                ? new ArrayList<>(Math.min(cap, ordered.size()))
                : List.of();

        for (T candidate : ordered) {
            double distance = distanceSquared.applyAsDouble(candidate);
            if (!Double.isFinite(distance) || distance < 0.0) {
                continue;
            }
            if (movement.size() < cap && distance <= movementRangeSquared) {
                movement.add(candidate);
            }
            if (communicationEnabled && communication.size() < cap && distance <= communicationRangeSquared) {
                communication.add(candidate);
            }
            if (movement.size() == cap && (!communicationEnabled || communication.size() == cap)) {
                break;
            }
        }
        return new Selection<>(List.copyOf(movement), List.copyOf(communication));
    }

    private SwarmNeighborSelectionPolicy() {}
}
