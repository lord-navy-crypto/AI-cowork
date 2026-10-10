package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

/**
 * A bounded game-only waypoint hold prevents a moving Skeleton from chasing
 * a freshly recalculated 3-block offset on every planner update.
 *
 * The caller must separately verify loaded terrain, ally clear lane, and
 * a real line of sight. This policy never authorizes pathfinding by itself.
 */
public final class SwarmOptionalGameWaypointCommitmentPolicy {
    public static final double MAX_PREVIOUS_DISTANCE = 4.5;
    public static final double ARRIVAL_DISTANCE = 0.85;
    public static final double MAX_PROPOSAL_SHIFT = 2.25;

    public static boolean keep(boolean previousMoveActive, boolean directVisible,
            double confidence, Vec2 self, Vec2 oldWaypoint, Vec2 freshWaypoint) {
        if (!previousMoveActive || !directVisible || !Double.isFinite(confidence)
                || confidence < 0.7 || !finite(self) || !finite(oldWaypoint)
                || !finite(freshWaypoint)) return false;

        double remaining = self.subtract(oldWaypoint).length();
        return remaining > ARRIVAL_DISTANCE
                && remaining <= MAX_PREVIOUS_DISTANCE
                && oldWaypoint.subtract(freshWaypoint).length()
                        <= MAX_PROPOSAL_SHIFT;
    }

    /** Sideways local game-world positions must not use the player's height. */
    public static double chooseNavigationHeight(boolean optionalLocalMove,
            double mobY, double targetY) {
        return optionalLocalMove && Double.isFinite(mobY)
                ? mobY : targetY;
    }

    private static boolean finite(Vec2 p) {
        return p != null && Double.isFinite(p.x()) && Double.isFinite(p.z());
    }

    private SwarmOptionalGameWaypointCommitmentPolicy() {}
}
