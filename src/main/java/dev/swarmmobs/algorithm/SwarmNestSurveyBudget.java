package dev.swarmmobs.algorithm;

/**
 * A single fixed-rate dimension-level expensive nest survey allowance.
 * Unlike individual Goal cooldowns, this limits the whole server dimension's
 * aggregate site checks when a large swarm becomes idle simultaneously.
 */
public final class SwarmNestSurveyBudget {
    private long nextAllowedTick = Long.MIN_VALUE;

    public boolean trySurvey(long tick, int minGapTicks) {
        if (tick < nextAllowedTick && tick >= 0) {
            return false;
        }
        nextAllowedTick = tick + Math.max(1, minGapTicks);
        return true;
    }
}
