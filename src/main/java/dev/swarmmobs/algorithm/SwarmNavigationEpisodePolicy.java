package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmBehaviorMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Decides when a running Minecraft NPC move Goal must discard waypoint
 * history from a different target or a different gameplay behavior phase.
 *
 * Route recovery cannot outlive the mission whose destination it was
 * calculated against. This policy does not alter attack permission or
 * require a new entity scan.
 */
public final class SwarmNavigationEpisodePolicy {
    public static final double RECOVERY_PLAN_SHIFT_BLOCKS = 6.0;

    /** ENGAGE <-> SEARCH and target UUID changes are new movement episodes. */
    public static boolean changed(boolean previousKnown, UUID previousTarget,
            SwarmBehaviorMode previousMode, UUID currentTarget,
            SwarmBehaviorMode currentMode) {
        return previousKnown && (!Objects.equals(previousTarget, currentTarget)
                || previousMode != currentMode);
    }

    /**
     * Cancel a locally generated detour if direct visual target evidence
     * causes the actual destination to change substantially. Do not do this
     * for SEARCH: sector sweeps should not reset recovery every search phase.
     */
    public static boolean abandonStaleRecovery(boolean recoveryActive,
            boolean directObservation, SwarmBehaviorMode mode,
            double anchorX, double anchorZ, double newestX, double newestZ) {
        if (!recoveryActive || !directObservation
                || mode != SwarmBehaviorMode.ENGAGE
                || !Double.isFinite(anchorX) || !Double.isFinite(anchorZ)
                || !Double.isFinite(newestX) || !Double.isFinite(newestZ)) {
            return false;
        }
        return Math.hypot(newestX - anchorX, newestZ - anchorZ)
                > RECOVERY_PLAN_SHIFT_BLOCKS;
    }

    private SwarmNavigationEpisodePolicy() {}
}
