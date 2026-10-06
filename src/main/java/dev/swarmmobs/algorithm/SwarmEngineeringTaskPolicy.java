package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmEngineeringTask;
import dev.swarmmobs.agent.SwarmRole;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic local claimant selection for Zombie engineering tasks.
 *
 * Lower score wins. The policy prefers nearby agents, preserves front-line
 * pressure when possible, and requires carried material for BRIDGE work.
 */
public final class SwarmEngineeringTaskPolicy {
    private static final double CHASER_ROLE_PENALTY = 3.0;
    private static final double FLANK_ROLE_PENALTY = 1.5;
    private static final double REAR_ROLE_PENALTY = 0.0;
    private static final double CARRIED_BLOCK_BONUS = 1.25;
    private static final double REQUESTER_STABILITY_BONUS = 0.35;

    public record Candidate(
            UUID entityId,
            double distanceSqr,
            SwarmRole role,
            int carriedBlocks,
            boolean requester,
            boolean meleeBusy
    ) {}

    public static UUID chooseClaimant(
            SwarmEngineeringTask.Type type,
            List<Candidate> candidates
    ) {
        if (type == null || candidates == null || candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .filter(candidate -> eligible(type, candidate))
                .min(
                        Comparator.comparingDouble(
                                        (Candidate candidate) -> score(type, candidate)
                                )
                                .thenComparing(candidate -> candidate.entityId().toString())
                )
                .map(Candidate::entityId)
                .orElse(null);
    }

    public static boolean eligible(
            SwarmEngineeringTask.Type type,
            Candidate candidate
    ) {
        if (type == null
                || candidate == null
                || candidate.entityId() == null
                || !Double.isFinite(candidate.distanceSqr())
                || candidate.distanceSqr() < 0.0
                || candidate.meleeBusy()) {
            return false;
        }

        return type != SwarmEngineeringTask.Type.BRIDGE
                || candidate.carriedBlocks() > 0;
    }

    public static double score(
            SwarmEngineeringTask.Type type,
            Candidate candidate
    ) {
        if (!eligible(type, candidate)) {
            return Double.POSITIVE_INFINITY;
        }

        double rolePenalty = switch (candidate.role() == null ? SwarmRole.CHASER : candidate.role()) {
            case CHASER -> CHASER_ROLE_PENALTY;
            case FLANK_LEFT, FLANK_RIGHT -> FLANK_ROLE_PENALTY;
            case REAR_PRESSURE, RANGED_SUPPORT -> REAR_ROLE_PENALTY;
        };

        double materialBonus = Math.min(4, Math.max(0, candidate.carriedBlocks()))
                * CARRIED_BLOCK_BONUS;

        return candidate.distanceSqr()
                + rolePenalty
                - materialBonus
                - (candidate.requester() ? REQUESTER_STABILITY_BONUS : 0.0);
    }

    private SwarmEngineeringTaskPolicy() {}
}
