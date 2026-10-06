package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

import java.util.List;

/**
 * Deterministic candidate generator for navigation recovery after an agent
 * fails to make progress. Feasibility and scoring are handled by the caller.
 */
public final class SwarmRecoveryCandidatePolicy {

    public record RecoveryCandidate(Vec2 waypoint, double lateralOffset) {}

    public static List<RecoveryCandidate> generate(
            Vec2 self,
            Vec2 destination,
            double recoveryDistance
    ) {
        if (self == null || destination == null || recoveryDistance <= 0.0) {
            return List.of();
        }

        Vec2 toTarget = destination.subtract(self);
        double distance = toTarget.length();
        if (distance < 1.0e-9) {
            return List.of();
        }

        Vec2 forward = toTarget.scale(1.0 / distance);
        Vec2 left = new Vec2(-forward.z(), forward.x());

        double d = recoveryDistance;
        double forwardStep = Math.min(distance, d * 0.75);
        double backwardStep = d * 0.50;

        return List.of(
                new RecoveryCandidate(
                        self.add(forward.scale(forwardStep)).add(left.scale(d)),
                        d
                ),
                new RecoveryCandidate(
                        self.add(forward.scale(forwardStep)).add(left.scale(-d)),
                        -d
                ),
                new RecoveryCandidate(
                        self.add(left.scale(d * 1.50)),
                        d * 1.50
                ),
                new RecoveryCandidate(
                        self.add(left.scale(-d * 1.50)),
                        -d * 1.50
                ),
                new RecoveryCandidate(
                        self.add(forward.scale(-backwardStep)).add(left.scale(d)),
                        d
                ),
                new RecoveryCandidate(
                        self.add(forward.scale(-backwardStep)).add(left.scale(-d)),
                        -d
                )
        );
    }

    private SwarmRecoveryCandidatePolicy() {}
}
