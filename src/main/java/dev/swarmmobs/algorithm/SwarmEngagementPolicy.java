package dev.swarmmobs.algorithm;

/**
 * Context separation for an individual Minecraft swarm mob:
 * WORK (normal ant/bee-like nest economy), ALERT (heard a target relay),
 * COMBAT (directly sees a live target or nearby engaged squad mate),
 * RECOVERY (brief cooldown after losing the target).
 *
 * These are behavioral scopes, not a new attack system. Existing vanilla
 * combat Goals remain the only authority over attacks and projectiles.
 */
public final class SwarmEngagementPolicy {
    public enum Mode { WORK, ALERT, COMBAT, RECOVERY }
    public static final int DIRECT_GRACE_TICKS = 20;
    public static final int RECOVERY_TICKS = 30;

    /** State transitions are pure and deterministic for a given local observation. */
    public static Mode next(Mode previous, boolean hasTargetEvidence,
                            boolean directOrSquadCombat,
                            long ticksSinceLastDirect, long ticksSinceLost) {
        Mode old = previous == null ? Mode.WORK : previous;
        if (hasTargetEvidence) {
            if (directOrSquadCombat
                    || (old == Mode.COMBAT && ticksSinceLastDirect >= 0
                        && ticksSinceLastDirect <= DIRECT_GRACE_TICKS)) {
                return Mode.COMBAT;
            }
            return Mode.ALERT;
        }
        if (old == Mode.WORK) return Mode.WORK;
        if (old != Mode.RECOVERY) return Mode.RECOVERY;
        return ticksSinceLost >= RECOVERY_TICKS ? Mode.WORK : Mode.RECOVERY;
    }

    /** Nest workers only resume physical jobs after a resolved disengagement. */
    public static boolean canDoNestWork(Mode mode) {
        return mode == Mode.WORK;
    }

    /** Extra local positioning may run only for a real active same-target squad. */
    public static boolean canCoordinateActiveSquad(Mode mode, int sameTargetPeers) {
        return mode == Mode.COMBAT && sameTargetPeers > 0;
    }

    private SwarmEngagementPolicy() {}
}
