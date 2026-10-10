package dev.swarmmobs.algorithm;

import java.util.UUID;
import dev.swarmmobs.agent.SwarmAgentArchetype;

/**
 * Optional SOFT tactical rounds for a mixed same-target squad.
 *
 * Minecraft remains real-time: these phases only choose safe support
 * positioning for the Skeleton. They never gate vanilla melee, projectiles,
 * Creeper fuse, engineering, or navigation priority. All peers with the
 * same target ID agree on their phase without a central controller.
 */
public final class SwarmTacticalRoundPolicy {
    public enum Phase { HOLD, COVER, ROTATE }
    public static final int ROUND_TICKS = 100;

    public static Phase phase(UUID targetId, long gameTick) {
        if (targetId == null || gameTick < 0) return Phase.HOLD;
        long squadOffset = Math.floorMod(targetId.getLeastSignificantBits(),3L);
        int index = (int) Math.floorMod(gameTick / ROUND_TICKS + squadOffset,3L);
        return Phase.values()[index];
    }

    /**
     * Each caste has a small spatial response to a synchronized phase.
     * These numbers are gameplay assumptions, not insect timing constants.
     * Close-range actual combat / vanilla AI always supersedes movement.
     */
    public static double formationRadiusMultiplier(Phase phase,
                                                   SwarmAgentArchetype archetype) {
        if (phase == null || archetype == null) return 1.0;
        return switch (phase) {
            case HOLD -> 1.0;
            case COVER -> switch (archetype) {
                case ASSAULT -> 1.10; // Zombie opens space for a safe bow lane
                case BREACHER -> 0.98; // Creeper keeps approach pressure
                case RANGED_SUPPORT -> 1.05;
                case FLANKER -> 1.04;
            };
            case ROTATE -> switch (archetype) {
                case ASSAULT -> 1.04;
                case BREACHER -> 1.0; // Don't shift vanilla fuse envelope
                case RANGED_SUPPORT -> 1.02;
                case FLANKER -> 1.06;
            };
        };
    }

    /**
     * Returns +1/-1 for a valid plan, 0 when neither lane is suitable.
     * Never deliberately chooses a blocked or teammate-occupied corridor.
     * A ROTATE round reverses the usual alternating formation side only
     * if that side is also clear. The other phases preserve the usual side.
     */
    public static int supportSide(Phase phase, int formationSlot,
                                  boolean positiveBlockClear,
                                  boolean negativeBlockClear,
                                  boolean positiveFriendlyClear,
                                  boolean negativeFriendlyClear) {
        boolean plus = positiveBlockClear && positiveFriendlyClear;
        boolean minus = negativeBlockClear && negativeFriendlyClear;
        if (!plus && !minus) return 0;
        if (plus && !minus) return +1;
        if (!plus) return -1;
        int stable = Math.floorMod(formationSlot,2) == 0 ? +1 : -1;
        return phase == Phase.ROTATE ? -stable : stable;
    }

    private SwarmTacticalRoundPolicy() {}
}
