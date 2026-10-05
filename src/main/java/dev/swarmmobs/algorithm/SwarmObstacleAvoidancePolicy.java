package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

/**
 * Chooses a short local detour when the immediate path toward a swarm destination
 * is visibly obstructed. Terrain sensing is supplied by the caller so this policy
 * remains deterministic and unit-testable.
 */
public final class SwarmObstacleAvoidancePolicy {

    public record Avoidance(Vec2 waypoint, boolean active, int side) {}

    public static Avoidance chooseWaypoint(
            Vec2 self,
            Vec2 destination,
            int entityId,
            boolean frontBlocked,
            boolean leftBlocked,
            boolean rightBlocked,
            double forwardDistance,
            double lateralDistance
    ) {
        if (!frontBlocked || forwardDistance <= 0.0 || lateralDistance <= 0.0) {
            return new Avoidance(destination, false, 0);
        }

        if (leftBlocked && rightBlocked) {
            return new Avoidance(destination, false, 0);
        }

        Vec2 toTarget = destination.subtract(self);
        double distance = toTarget.length();
        if (distance < 1.0e-9) {
            return new Avoidance(destination, false, 0);
        }

        Vec2 forward = toTarget.scale(1.0 / distance);
        Vec2 left = new Vec2(-forward.z(), forward.x());

        int side;
        if (!leftBlocked && rightBlocked) {
            side = 1;
        } else if (leftBlocked && !rightBlocked) {
            side = -1;
        } else {
            side = Math.floorMod(entityId, 2) == 0 ? 1 : -1;
        }

        Vec2 waypoint = self
                .add(forward.scale(Math.min(distance, forwardDistance)))
                .add(left.scale(lateralDistance * side));

        return new Avoidance(waypoint, true, side);
    }

    private SwarmObstacleAvoidancePolicy() {}
}
