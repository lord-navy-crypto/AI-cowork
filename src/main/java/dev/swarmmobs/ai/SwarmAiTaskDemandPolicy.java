package dev.swarmmobs.ai;

import dev.swarmmobs.agent.SwarmTaskType;

public final class SwarmAiTaskDemandPolicy {

    public static double multiplier(
            SwarmStrategyDecision.Mode mode,
            SwarmTaskType task
    ) {
        if (mode == null || task == null) {
            return 1.0;
        }

        return switch (mode) {
            case ENCIRCLE -> switch (task) {
                case FLANK, SEARCH -> 1.25;
                case BREACH -> 0.90;
                default -> 1.0;
            };
            case CONCENTRATE -> switch (task) {
                case BREACH, RANGED_SUPPORT -> 1.20;
                case FLANK -> 0.90;
                default -> 1.0;
            };
            case SEARCH -> task == SwarmTaskType.SEARCH ? 1.35 : 0.95;
            case REGROUP -> task == SwarmTaskType.RESERVE ? 1.35 : 0.90;
            case BASELINE -> 1.0;
        };
    }

    public static double apply(
            double deterministicDemand,
            SwarmStrategyDecision.Mode mode,
            SwarmTaskType task
    ) {
        double value = deterministicDemand * multiplier(mode, task);
        return Math.max(0.0, Math.min(1.0, value));
    }

    private SwarmAiTaskDemandPolicy() {}
}
