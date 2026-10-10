package dev.swarmmobs.algorithm;

/**
 * Explicit tick budgets for optional Minecraft NPC positioning.
 * Optional sideways movement must never prevent vanilla attacks indefinitely.
 */
public final class SwarmGameHandoffTimeoutPolicy {
    public static final long SKELETON_MAX_MOVE_TICKS = 30;
    public static final long SKELETON_RETRY_COOLDOWN_TICKS = 50;
    public static final long ZOMBIE_MAX_FLANK_TICKS = 18;
    public static final long ZOMBIE_RETRY_COOLDOWN_TICKS = 30;

    public static boolean timedOut(long now, long startedAt, long limitTicks) {
        return now >= 0 && startedAt >= 0 && now >= startedAt
                && limitTicks >= 0 && now - startedAt >= limitTicks;
    }

    public static long nextEligibleTick(long now, long cooldownTicks) {
        if (now < 0) return 0;
        if (cooldownTicks <= 0) return now;
        if (Long.MAX_VALUE-now < cooldownTicks) return Long.MAX_VALUE;
        return now+cooldownTicks;
    }

    private SwarmGameHandoffTimeoutPolicy() {}
}
