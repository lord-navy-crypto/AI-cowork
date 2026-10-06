package dev.swarmmobs.ai;

/**
 * Server-global bounded high-level AI strategy overlay.
 *
 * It never stores coordinates or per-agent actions. Only sanitized aggregate
 * strategy multipliers may become active, and they expire automatically.
 */
public final class SwarmAiActiveState {
    private static final SwarmStrategyDecision BASELINE =
            SwarmStrategyDecision.baseline(
                    "deterministic-active-baseline",
                    "No active AI strategy; deterministic multipliers are in force."
            );

    private static SwarmStrategyDecision activeDecision = BASELINE;
    private static boolean active;
    private static long activatedTick = Long.MIN_VALUE;
    private static long expiresTick = Long.MIN_VALUE;

    private static SwarmStrategyDecision pendingDecision;
    private static long pendingEligibleTick = Long.MIN_VALUE;
    private static int pendingTtlTicks;

    public static synchronized void consider(
            SwarmStrategyDecision decision,
            long gameTick,
            int ttlTicks,
            int minHoldTicks
    ) {
        SwarmStrategyDecision next = decision == null ? BASELINE : decision.sanitized();

        if (next.providerId().startsWith("deterministic")) {
            return;
        }

        int ttl = Math.max(1, ttlTicks);
        int hold = Math.max(0, minHoldTicks);

        expireIfNeeded(gameTick);
        promotePendingIfEligible(gameTick);

        if (!active) {
            activate(next, gameTick, ttl);
            return;
        }

        if (activeDecision.mode() == next.mode()) {
            activate(next, gameTick, ttl);
            pendingDecision = null;
            pendingEligibleTick = Long.MIN_VALUE;
            pendingTtlTicks = 0;
            return;
        }

        long eligibleTick = activatedTick + hold;
        if (gameTick >= eligibleTick) {
            activate(next, gameTick, ttl);
            pendingDecision = null;
            pendingEligibleTick = Long.MIN_VALUE;
            pendingTtlTicks = 0;
            return;
        }

        pendingDecision = next;
        pendingEligibleTick = eligibleTick;
        pendingTtlTicks = ttl;
    }

    public static synchronized Snapshot snapshot(long gameTick) {
        expireIfNeeded(gameTick);
        promotePendingIfEligible(gameTick);

        return new Snapshot(
                active,
                active ? activeDecision : BASELINE,
                activatedTick,
                expiresTick,
                pendingDecision,
                pendingEligibleTick
        );
    }

    public static synchronized void clear() {
        active = false;
        activeDecision = BASELINE;
        activatedTick = Long.MIN_VALUE;
        expiresTick = Long.MIN_VALUE;
        pendingDecision = null;
        pendingEligibleTick = Long.MIN_VALUE;
        pendingTtlTicks = 0;
    }

    private static void expireIfNeeded(long gameTick) {
        if (active && expiresTick != Long.MIN_VALUE && gameTick >= expiresTick) {
            clear();
        }
    }

    private static void promotePendingIfEligible(long gameTick) {
        if (!active || pendingDecision == null || gameTick < pendingEligibleTick) {
            return;
        }

        SwarmStrategyDecision next = pendingDecision;
        int ttl = Math.max(1, pendingTtlTicks);
        pendingDecision = null;
        pendingEligibleTick = Long.MIN_VALUE;
        pendingTtlTicks = 0;
        activate(next, gameTick, ttl);
    }

    private static void activate(
            SwarmStrategyDecision decision,
            long gameTick,
            int ttlTicks
    ) {
        active = true;
        activeDecision = decision.sanitized();
        activatedTick = gameTick;
        expiresTick = gameTick + Math.max(1, ttlTicks);
    }

    public record Snapshot(
            boolean active,
            SwarmStrategyDecision decision,
            long activatedTick,
            long expiresTick,
            SwarmStrategyDecision pendingDecision,
            long pendingEligibleTick
    ) {
        public double formationRadiusMultiplier() {
            return active ? decision.formationRadiusMultiplier() : 1.0;
        }

        public double separationMultiplier() {
            return active ? decision.separationMultiplier() : 1.0;
        }

        public double cohesionMultiplier() {
            return active ? decision.cohesionMultiplier() : 1.0;
        }

        public double searchRadiusMultiplier() {
            return active ? decision.searchRadiusMultiplier() : 1.0;
        }
    }

    private SwarmAiActiveState() {}
}
