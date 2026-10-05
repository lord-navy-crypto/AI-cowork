package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

/**
 * Deterministic local navigation recovery for agents that stop making progress
 * toward a planned destination.
 */
public final class SwarmNavigationRecoveryPolicy {

    public record Recovery(Vec2 waypoint, boolean active) {}

    public static Recovery recoveryWaypoint(
            Vec2 self,
            Vec2 destination,
            int entityId,
            double lateralDistance
    ) {
        Vec2 toTarget = destination.subtract(self);
        double distance = toTarget.length();
        if (distance < 1.0e-9 || lateralDistance <= 0.0) {
            return new Recovery(destination, false);
        }

        Vec2 forward = toTarget.scale(1.0 / distance);
        Vec2 lateral = new Vec2(-forward.z(), forward.x());
        double sign = Math.floorMod(entityId, 2) == 0 ? 1.0 : -1.0;

        Vec2 waypoint = self
                .add(forward.scale(Math.min(distance, lateralDistance)))
                .add(lateral.scale(lateralDistance * sign));

        return new Recovery(waypoint, true);
    }

    private SwarmNavigationRecoveryPolicy() {}
}
