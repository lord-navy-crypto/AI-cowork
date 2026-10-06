package dev.swarmmobs.algorithm;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Generic deterministic local auction winner selector.
 *
 * Higher utility wins. UUID provides a stable tie-break so independent agents
 * observing the same candidate set converge on the same claimant.
 */
public final class SwarmTaskAllocator {

    public record Offer(
            UUID entityId,
            double utility,
            boolean eligible
    ) {}

    public static UUID winner(List<Offer> offers) {
        if (offers == null || offers.isEmpty()) {
            return null;
        }

        return offers.stream()
                .filter(offer -> offer != null
                        && offer.entityId() != null
                        && offer.eligible()
                        && Double.isFinite(offer.utility()))
                .max(
                        Comparator.comparingDouble(Offer::utility)
                                .thenComparing(
                                        offer -> offer.entityId().toString(),
                                        Comparator.reverseOrder()
                                )
                )
                .map(Offer::entityId)
                .orElse(null);
    }

    private SwarmTaskAllocator() {}
}
