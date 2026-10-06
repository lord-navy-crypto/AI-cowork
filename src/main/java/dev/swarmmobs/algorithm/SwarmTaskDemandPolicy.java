package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmTaskType;

public final class SwarmTaskDemandPolicy {

    public record Signals(
            boolean searchMode,
            boolean routeBlocked,
            boolean hasEngineeringMaterial,
            boolean dedicatedFlankCoverage,
            boolean hasBreacher,
            boolean hasRangedSupport,
            double targetConfidence
    ) {}

    public static double demand(SwarmTaskType task, Signals signals) {
        if (task == null || signals == null) {
            return 0.0;
        }

        double confidence = clamp01(signals.targetConfidence());

        return clamp01(switch (task) {
            case SEARCH -> signals.searchMode() ? 1.0 : 0.10 * (1.0 - confidence);
            case ENGINEERING -> signals.routeBlocked() ? 1.0 : 0.0;
            case MATERIAL -> signals.routeBlocked()
                    ? (signals.hasEngineeringMaterial() ? 0.35 : 0.90)
                    : 0.05;
            case FLANK -> signals.searchMode()
                    ? 0.20
                    : (signals.dedicatedFlankCoverage() ? 0.35 : 0.85);
            case BREACH -> signals.searchMode()
                    ? 0.15
                    : (signals.hasBreacher() ? 0.65 : 0.90);
            case RANGED_SUPPORT -> signals.searchMode()
                    ? 0.25
                    : (signals.hasRangedSupport() ? 0.70 : 0.95);
            case RESERVE -> signals.searchMode() ? 0.20 : 0.30;
        });
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private SwarmTaskDemandPolicy() {}
}
