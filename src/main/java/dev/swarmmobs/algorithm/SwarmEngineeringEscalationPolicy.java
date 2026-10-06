package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmPlannerContext;

/**
 * Decides when normal navigation has failed enough to justify bounded Zombie
 * engineering. Ordinary obstacle detours should be preferred; engineering is
 * allowed immediately only when no feasible local candidate exists, or after
 * the mob has escalated into RECOVERY and still has blocked/unreachable route
 * evidence.
 */
public final class SwarmEngineeringEscalationPolicy {
    private SwarmEngineeringEscalationPolicy() {}

    public static boolean shouldEscalate(
            SwarmPlannerContext context,
            int blockedCount,
            int unreachableCount,
            int feasibleCount
    ) {
        SwarmPlannerContext actual = context == null
                ? SwarmPlannerContext.NONE
                : context;

        int blocked = Math.max(0, blockedCount);
        int unreachable = Math.max(0, unreachableCount);
        int feasible = Math.max(0, feasibleCount);
        boolean hasObstacleEvidence = blocked > 0 || unreachable > 0;

        if (actual == SwarmPlannerContext.NONE || !hasObstacleEvidence) {
            return false;
        }

        if (feasible == 0) {
            return true;
        }

        return actual == SwarmPlannerContext.RECOVERY;
    }
}
