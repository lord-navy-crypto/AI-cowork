package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;

import java.util.List;
import java.util.UUID;

public final class SwarmCombatPlanner {
    private static final double EPS = 1.0e-9;

    public record Vec2(double x, double z) {
        public Vec2 add(Vec2 other) {
            return new Vec2(x + other.x, z + other.z);
        }

        public Vec2 subtract(Vec2 other) {
            return new Vec2(x - other.x, z - other.z);
        }

        public Vec2 scale(double factor) {
            return new Vec2(x * factor, z * factor);
        }

        public double length() {
            return Math.sqrt(x * x + z * z);
        }

        public Vec2 normalized() {
            double length = length();
            return length < EPS ? new Vec2(0.0, 0.0) : scale(1.0 / length);
        }
    }

    public record Plan(
            Vec2 destination,
            SwarmRole role,
            int formationSlot,
            double separationMagnitude,
            double cohesionMagnitude
    ) {}

    public static Plan plan(
            UUID agentId,
            Vec2 self,
            Vec2 target,
            Vec2 targetForward,
            List<Vec2> neighbors,
            int formationSlots,
            double formationRadius,
            double separationRadius,
            double separationWeight,
            double cohesionWeight
    ) {
        int slots = Math.max(4, formationSlots);
        int slot = formationSlot(agentId, slots);
        SwarmRole role = roleForSlot(slot);

        Vec2 forward = targetForward.normalized();
        if (forward.length() < EPS) {
            forward = new Vec2(0.0, 1.0);
        }
        // In the X/Z plane this vector points to the target's local right.
        Vec2 right = new Vec2(forward.z(), -forward.x());

        int lane = slot / 4;
        int laneCount = (slots + 3) / 4;
        double laneSpacing = Math.min(2.0, Math.max(0.75, formationRadius * 0.35));
        double laneOffset = (lane - (laneCount - 1) / 2.0) * laneSpacing;

        Vec2 base = switch (role) {
            case CHASER -> target.add(right.scale(laneOffset));
            case FLANK_LEFT -> target
                    .add(right.scale(-formationRadius))
                    .add(forward.scale(laneOffset));
            case FLANK_RIGHT -> target
                    .add(right.scale(formationRadius))
                    .add(forward.scale(laneOffset));
            case REAR_PRESSURE -> target
                    .add(forward.scale(-formationRadius))
                    .add(right.scale(laneOffset));
        };

        Vec2 separation = separation(self, neighbors, separationRadius);
        Vec2 cohesion = cohesion(self, neighbors);

        Vec2 destination = base
                .add(separation.scale(separationWeight))
                .add(cohesion.scale(cohesionWeight));

        return new Plan(
                destination,
                role,
                slot,
                separation.length(),
                cohesion.length()
        );
    }

    public static int formationSlot(UUID agentId, int formationSlots) {
        int slots = Math.max(1, formationSlots);
        return Math.floorMod(agentId.hashCode(), slots);
    }

    public static SwarmRole roleForSlot(int slot) {
        return switch (Math.floorMod(slot, 4)) {
            case 0 -> SwarmRole.CHASER;
            case 1 -> SwarmRole.FLANK_LEFT;
            case 2 -> SwarmRole.FLANK_RIGHT;
            default -> SwarmRole.REAR_PRESSURE;
        };
    }

    public static Vec2 separation(Vec2 self, List<Vec2> neighbors, double radius) {
        if (neighbors.isEmpty() || radius <= 0.0) {
            return new Vec2(0.0, 0.0);
        }

        Vec2 sum = new Vec2(0.0, 0.0);
        for (Vec2 neighbor : neighbors) {
            Vec2 delta = self.subtract(neighbor);
            double distance = delta.length();
            if (distance < EPS || distance >= radius) {
                continue;
            }

            double strength = (radius - distance) / radius;
            sum = sum.add(delta.normalized().scale(strength));
        }

        return sum;
    }

    public static Vec2 cohesion(Vec2 self, List<Vec2> neighbors) {
        if (neighbors.isEmpty()) {
            return new Vec2(0.0, 0.0);
        }

        double x = 0.0;
        double z = 0.0;
        for (Vec2 neighbor : neighbors) {
            x += neighbor.x();
            z += neighbor.z();
        }

        Vec2 centroid = new Vec2(x / neighbors.size(), z / neighbors.size());
        return centroid.subtract(self).normalized();
    }

    private SwarmCombatPlanner() {}
}
