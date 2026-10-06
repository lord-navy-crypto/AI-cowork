package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmTaskType;

/**
 * Local task-saturation feedback for decentralized division of labor.
 *
 * Each agent estimates workforce occupancy from nearby peers only. When the
 * locally desired workforce for a task is already satisfied, new recruits see
 * a strong demand reduction while incumbents retain a softer demand floor.
 * This prevents endless over-recruitment without requiring a central manager.
 */
public final class SwarmTaskSaturationPolicy {
    private static final double NEW_RECRUIT_SATURATED_MULTIPLIER = 0.18;
    private static final double INCUMBENT_SATURATED_MULTIPLIER = 0.72;

    public static double adjustedDemand(
            SwarmTaskType task,
            double baseDemand,
            int localGroupSize,
            int peerOccupancy,
            SwarmTaskType currentTask
    ) {
        double demand = clamp01(baseDemand);
        if (task == null || task == SwarmTaskType.RESERVE || demand <= 0.0) {
            return demand;
        }

        int desired = desiredHeadcount(task, demand, localGroupSize);
        int peers = Math.max(0, peerOccupancy);

        if (peers < desired) {
            return demand;
        }

        double multiplier = currentTask == task
                ? INCUMBENT_SATURATED_MULTIPLIER
                : NEW_RECRUIT_SATURATED_MULTIPLIER;

        int excess = Math.max(0, peers - desired);
        double excessPenalty = 1.0 / (1.0 + 0.35 * excess);
        return clamp01(demand * multiplier * excessPenalty);
    }

    public static int desiredHeadcount(
            SwarmTaskType task,
            double baseDemand,
            int localGroupSize
    ) {
        if (task == null || task == SwarmTaskType.RESERVE) {
            return 0;
        }

        double demand = clamp01(baseDemand);
        if (demand <= 0.0) {
            return 0;
        }

        int group = Math.max(1, localGroupSize);
        double share = switch (task) {
            case ENGINEERING -> 0.20;
            case MATERIAL -> 0.15;
            case FLANK -> 0.30;
            case BREACH -> 0.20;
            case RANGED_SUPPORT -> 0.30;
            case SEARCH -> 0.30;
            case RESERVE -> 0.0;
        };

        return Math.max(1, (int) Math.ceil(group * demand * share));
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private SwarmTaskSaturationPolicy() {}
}
