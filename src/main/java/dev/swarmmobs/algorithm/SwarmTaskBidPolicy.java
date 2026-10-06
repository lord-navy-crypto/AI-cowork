package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmTaskType;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SwarmTaskBidPolicy {
    public static final double SWITCHING_COST = 0.18;
    public static final double EXPERIENCE_WEIGHT = 0.20;
    public static final double BUSY_PENALTY = 0.55;
    public static final double DISTANCE_WEIGHT = 0.08;

    public record Candidate(
            UUID entityId,
            SwarmAgentArchetype archetype,
            SwarmTaskType currentTask,
            double experience,
            double normalizedDistance,
            boolean combatBusy
    ) {}

    public record Bid(UUID entityId, SwarmTaskType task, double utility) {}

    public static Bid bid(
            Candidate candidate,
            SwarmTaskType task,
            double demand
    ) {
        if (candidate == null || candidate.entityId() == null || task == null) {
            return new Bid(null, task, Double.NEGATIVE_INFINITY);
        }

        double capability = capability(candidate.archetype(), task);
        if (capability <= 0.0) {
            return new Bid(candidate.entityId(), task, Double.NEGATIVE_INFINITY);
        }

        double boundedDemand = clamp01(demand);
        double threshold = responseThreshold(candidate.entityId(), task, capability);
        if (task != SwarmTaskType.RESERVE && boundedDemand < threshold) {
            return new Bid(candidate.entityId(), task, Double.NEGATIVE_INFINITY);
        }

        double utility = boundedDemand * capability
                + clamp01(candidate.experience()) * EXPERIENCE_WEIGHT
                - Math.max(0.0, candidate.normalizedDistance()) * DISTANCE_WEIGHT
                - (candidate.combatBusy() ? BUSY_PENALTY : 0.0)
                - (candidate.currentTask() != null && candidate.currentTask() != task
                    ? SWITCHING_COST
                    : 0.0);

        return new Bid(candidate.entityId(), task, utility);
    }

    public static UUID winner(
            SwarmTaskType task,
            double demand,
            List<Candidate> candidates
    ) {
        if (task == null || candidates == null || candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .map(candidate -> bid(candidate, task, demand))
                .filter(bid -> Double.isFinite(bid.utility()))
                .max(
                        Comparator.comparingDouble(Bid::utility)
                                .thenComparing(bid -> bid.entityId().toString(), Comparator.reverseOrder())
                )
                .map(Bid::entityId)
                .orElse(null);
    }

    public static double responseThreshold(
            UUID entityId,
            SwarmTaskType task,
            double capability
    ) {
        if (entityId == null || task == null) {
            return 1.0;
        }

        long mixed = entityId.getMostSignificantBits()
                ^ Long.rotateLeft(entityId.getLeastSignificantBits(), 17)
                ^ (0x9E3779B97F4A7C15L * (task.ordinal() + 1L));
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;

        double unit = (double) (mixed & 0xffffL) / 65535.0;
        double specializationPenalty = (1.0 - clamp01(capability)) * 0.25;
        double threshold = 0.22 + unit * 0.42 + specializationPenalty;

        if (task == SwarmTaskType.RESERVE) {
            threshold = 0.0;
        }

        return Math.max(0.10, Math.min(0.90, threshold));
    }

    public static double capability(
            SwarmAgentArchetype archetype,
            SwarmTaskType task
    ) {
        if (archetype == null || task == null) {
            return 0.0;
        }

        return switch (archetype) {
            case ASSAULT -> switch (task) {
                case ENGINEERING -> 1.00;
                case MATERIAL -> 0.95;
                case BREACH -> 0.75;
                case FLANK -> 0.55;
                case SEARCH -> 0.45;
                case RESERVE -> 0.65;
                case RANGED_SUPPORT -> 0.10;
            };
            case RANGED_SUPPORT -> switch (task) {
                case RANGED_SUPPORT -> 1.00;
                case SEARCH -> 0.45;
                case RESERVE -> 0.70;
                case FLANK -> 0.20;
                default -> 0.05;
            };
            case FLANKER -> switch (task) {
                case FLANK -> 1.00;
                case SEARCH -> 0.95;
                case RESERVE -> 0.55;
                case BREACH -> 0.25;
                default -> 0.05;
            };
            case BREACHER -> switch (task) {
                case BREACH -> 1.00;
                case RESERVE -> 0.70;
                case FLANK -> 0.20;
                default -> 0.05;
            };
        };
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private SwarmTaskBidPolicy() {}
}
