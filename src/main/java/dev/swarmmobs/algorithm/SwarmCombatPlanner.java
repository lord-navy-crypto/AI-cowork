package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;

import java.util.ArrayList;
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
            double cohesionMagnitude,
            double alignmentMagnitude,
            double steeringMagnitude
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
        return planForSlot(
                formationSlot(agentId, Math.max(4, formationSlots)),
                self,
                target,
                targetForward,
                neighbors,
                formationSlots,
                formationRadius,
                separationRadius,
                separationWeight,
                cohesionWeight
        );
    }

    public static Plan planForSlot(
            int assignedSlot,
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
        return planForSlotWithMotion(
                assignedSlot,
                self,
                new Vec2(0.0, 0.0),
                target,
                targetForward,
                neighbors,
                List.of(),
                formationSlots,
                formationRadius,
                separationRadius,
                separationWeight,
                cohesionWeight,
                0.0,
                Double.POSITIVE_INFINITY
        );
    }

    public static Plan planForSlotWithMotion(
            int assignedSlot,
            Vec2 self,
            Vec2 selfVelocity,
            Vec2 target,
            Vec2 targetForward,
            List<Vec2> neighborPositions,
            List<Vec2> neighborVelocities,
            int formationSlots,
            double formationRadius,
            double separationRadius,
            double separationWeight,
            double cohesionWeight,
            double alignmentWeight,
            double maxSteeringCorrection
    ) {
        int slots = Math.max(4, formationSlots);
        int slot = Math.floorMod(assignedSlot, slots);
        SwarmRole role = roleForSlot(slot);

        Vec2 forward = targetForward.normalized();
        if (forward.length() < EPS) {
            forward = new Vec2(0.0, 1.0);
        }

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

        Vec2 separation = separation(self, neighborPositions, separationRadius);
        Vec2 cohesion = cohesion(self, neighborPositions);
        Vec2 alignment = alignment(selfVelocity, neighborVelocities);

        Vec2 rawSteering = separation.scale(separationWeight)
                .add(cohesion.scale(cohesionWeight))
                .add(alignment.scale(alignmentWeight));

        Vec2 steering = clampLength(rawSteering, maxSteeringCorrection);
        Vec2 destination = base.add(steering);

        return new Plan(
                destination,
                role,
                slot,
                separation.length(),
                cohesion.length(),
                alignment.length(),
                steering.length()
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

    public static Vec2 alignment(Vec2 selfVelocity, List<Vec2> neighborVelocities) {
        if (neighborVelocities.isEmpty()) {
            return new Vec2(0.0, 0.0);
        }

        List<Vec2> moving = new ArrayList<>();
        for (Vec2 velocity : neighborVelocities) {
            if (velocity != null && velocity.length() >= EPS) {
                moving.add(velocity);
            }
        }

        if (moving.isEmpty()) {
            return new Vec2(0.0, 0.0);
        }

        double x = 0.0;
        double z = 0.0;
        for (Vec2 velocity : moving) {
            Vec2 direction = velocity.normalized();
            x += direction.x();
            z += direction.z();
        }

        Vec2 desiredDirection = new Vec2(x / moving.size(), z / moving.size()).normalized();
        Vec2 currentDirection = selfVelocity == null
                ? new Vec2(0.0, 0.0)
                : selfVelocity.normalized();

        if (currentDirection.length() < EPS) {
            return desiredDirection;
        }

        return desiredDirection.subtract(currentDirection);
    }

    public static Vec2 clampLength(Vec2 vector, double maxLength) {
        if (vector == null) {
            return new Vec2(0.0, 0.0);
        }

        if (!Double.isFinite(maxLength)) {
            return vector;
        }

        if (maxLength <= 0.0) {
            return new Vec2(0.0, 0.0);
        }

        double length = vector.length();
        if (length <= maxLength || length < EPS) {
            return vector;
        }

        return vector.scale(maxLength / length);
    }

    private SwarmCombatPlanner() {}
}
