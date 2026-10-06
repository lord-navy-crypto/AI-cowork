package dev.swarmmobs.algorithm;

/**
 * Bounded execution lease for an engineering action that has already been
 * claimed. Advertisement TTL limits stale task discovery; it must not abort a
 * legitimate break/bridge action before the action's own deterministic work
 * budget can finish.
 */
public final class SwarmEngineeringExecutionLeasePolicy {
    private static final int START_MARGIN_TICKS = 40;

    public static long deadline(
            long gameTick,
            long advertisedExpiresTick,
            int requiredWorkTicks
    ) {
        long now = Math.max(0L, gameTick);
        long advertised = Math.max(now, advertisedExpiresTick);
        long work = Math.max(1L, requiredWorkTicks);

        long completionBudget;
        try {
            completionBudget = Math.addExact(now, Math.addExact(work, START_MARGIN_TICKS));
        } catch (ArithmeticException ignored) {
            completionBudget = Long.MAX_VALUE;
        }

        return Math.max(advertised, completionBudget);
    }

    private SwarmEngineeringExecutionLeasePolicy() {}
}
