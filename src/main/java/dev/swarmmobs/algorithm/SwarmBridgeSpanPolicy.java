package dev.swarmmobs.algorithm;

/**
 * Pure bounded decision policy for committing to a small bridge span.
 */
public final class SwarmBridgeSpanPolicy {

    public record Evaluation(
            int gapLength,
            boolean landingFound,
            int availableMaterials,
            int maxSpan,
            boolean allowed
    ) {}

    public static Evaluation evaluate(
            int gapLength,
            boolean landingFound,
            int availableMaterials,
            int maxSpan
    ) {
        int gap = Math.max(0, gapLength);
        int materials = Math.max(0, availableMaterials);
        int boundedMax = Math.max(1, maxSpan);

        boolean allowed = gap > 0
                && landingFound
                && gap <= boundedMax
                && materials >= gap;

        return new Evaluation(
                gap,
                landingFound,
                materials,
                boundedMax,
                allowed
        );
    }

    private SwarmBridgeSpanPolicy() {}
}
