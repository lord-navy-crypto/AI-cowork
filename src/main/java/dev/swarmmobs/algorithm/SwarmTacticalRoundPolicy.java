package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;

/**
 * Reactive, local squad positioning phases inspired by robot feedback control.
 *
 * "Round" means a stable decision interval, NOT an attack turn. Inputs come
 * from already-sensed same-target peers and existing navigation evidence.
 * We deliberately never change vanilla attacks, Creeper fuse or Goal priority.
 * No clock-only rotation, global coordinator, chunk scan or extra target query.
 */
public final class SwarmTacticalRoundPolicy {
    public enum Phase { HOLD, COVER, ROTATE }

    /** Only meaningful observations can request a phase change. */
    public record Signals(boolean freshContact, boolean supportPresent,
                          boolean frontlinePresent, boolean congested,
                          boolean navigationBlocked) {}

    /**
     * Pending candidates must be consistent across multiple planning samples;
     * a phase is allowed to settle before another switch is accepted.
     * All fields are transient and scoped to the current target.
     */
    public record Decision(Phase phase, Phase candidate, long candidateSince,
                           long phaseSince, long switchCount) {}

    public static final int CONFIRM_TICKS = 12;
    public static final int MIN_PHASE_HOLD_TICKS = 20;

    public static Decision initial() {
        return new Decision(Phase.HOLD, null, Long.MIN_VALUE,
                Long.MIN_VALUE, 0);
    }

    public static Phase recommend(Signals situation) {
        if (situation == null || !situation.freshContact()) return Phase.HOLD;
        // A locally observed obstruction overrides routine formation plans,
        // but the phase transition still requires stable evidence.
        if (situation.congested() || situation.navigationBlocked()) {
            return Phase.ROTATE;
        }
        if (situation.supportPresent() && situation.frontlinePresent()) {
            return Phase.COVER;
        }
        return Phase.HOLD;
    }

    /**
     * Advance only when the requested phase stays consistent, NOT when a
     * fixed wall-clock round finishes. Old/suddenly negative timestamps are
     * ignored. Confirmed switches can be counted for debug/performance tests.
     */
    public static Decision advance(Decision state, Phase requested, long gameTick) {
        Decision previous = state == null ? initial() : state;
        Phase next = requested == null ? Phase.HOLD : requested;
        if (gameTick < 0) return previous;

        if (next == previous.phase()) {
            return new Decision(previous.phase(), null, Long.MIN_VALUE,
                    previous.phaseSince(), previous.switchCount());
        }
        if (next != previous.candidate() || previous.candidateSince() > gameTick) {
            return new Decision(previous.phase(), next, gameTick,
                    previous.phaseSince(), previous.switchCount());
        }

        long candidateDuration = gameTick - previous.candidateSince();
        long phaseDuration = previous.phaseSince() == Long.MIN_VALUE
                ? Long.MAX_VALUE : Math.max(0L, gameTick - previous.phaseSince());
        if (candidateDuration < CONFIRM_TICKS
                || phaseDuration < MIN_PHASE_HOLD_TICKS) {
            return previous;
        }

        return new Decision(next, null, Long.MIN_VALUE, gameTick,
                previous.switchCount() + 1);
    }

    /**
     * Small spatial adjustments for the Minecraft mob archetypes.
     * This is a game heuristic, not a physical robot's actuator model.
     */
    public static double formationRadiusMultiplier(Phase phase,
                                                   SwarmAgentArchetype archetype) {
        if (phase == null || archetype == null) return 1.0;
        return switch (phase) {
            case HOLD -> 1.0;
            case COVER -> switch (archetype) {
                case ASSAULT -> 1.10;
                case BREACHER -> 0.98;
                case RANGED_SUPPORT -> 1.05;
                case FLANKER -> 1.04;
            };
            case ROTATE -> switch (archetype) {
                case ASSAULT -> 1.04;
                case BREACHER -> 1.0;
                case RANGED_SUPPORT -> 1.02;
                case FLANKER -> 1.06;
            };
        };
    }

    /**
     * Returns +1/-1 for a verified support lane or 0 when both are unsafe.
     * Rotation never overrides terrain collision or ally clearance checks.
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
        int stable = Math.floorMod(formationSlot, 2) == 0 ? +1 : -1;
        return phase == Phase.ROTATE ? -stable : stable;
    }

    private SwarmTacticalRoundPolicy() {}
}
