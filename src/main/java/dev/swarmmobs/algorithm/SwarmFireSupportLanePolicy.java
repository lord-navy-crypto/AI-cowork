package dev.swarmmobs.algorithm;

import java.util.List;

/**
 * Separates ranged-support firing positions from a local breacher ingress lane.
 *
 * The policy operates only on planned destinations. It does not replace vanilla
 * aiming or bow combat; it merely shifts Skeleton support positions laterally
 * when a Creeper is pushing the same target.
 */
public final class SwarmFireSupportLanePolicy {
    public static final double MIN_LATERAL_OFFSET = 1.5;
    public static final double BREACHER_CLEARANCE = 2.25;
    private static final double EPS = 1.0e-9;

    public static SwarmCombatPlanner.Vec2 apply(
            SwarmCombatPlanner.Vec2 baseDestination,
            SwarmCombatPlanner.Vec2 target,
            SwarmCombatPlanner.Vec2 targetForward,
            int formationSlot,
            List<SwarmCombatPlanner.Vec2> breacherPositions
    ) {
        if (baseDestination == null
                || target == null
                || targetForward == null
                || breacherPositions == null
                || breacherPositions.isEmpty()) {
            return baseDestination;
        }

        SwarmCombatPlanner.Vec2 forward = targetForward.normalized();
        if (forward.length() < EPS) {
            forward = new SwarmCombatPlanner.Vec2(0.0, 1.0);
        }
        SwarmCombatPlanner.Vec2 right = new SwarmCombatPlanner.Vec2(forward.z(), -forward.x());

        double breacherLateral = 0.0;
        int samples = 0;
        for (SwarmCombatPlanner.Vec2 breacher : breacherPositions) {
            if (breacher == null) {
                continue;
            }
            SwarmCombatPlanner.Vec2 relative = breacher.subtract(target);
            breacherLateral += relative.x() * right.x() + relative.z() * right.z();
            samples++;
        }

        if (samples == 0) {
            return baseDestination;
        }

        breacherLateral /= samples;

        double desiredSign;
        if (Math.abs(breacherLateral) >= 0.35) {
            desiredSign = breacherLateral > 0.0 ? -1.0 : 1.0;
        } else {
            desiredSign = Math.floorMod(formationSlot, 2) == 0 ? 1.0 : -1.0;
        }

        SwarmCombatPlanner.Vec2 destinationRelative = baseDestination.subtract(target);
        double currentLateral =
                destinationRelative.x() * right.x() + destinationRelative.z() * right.z();

        double desiredMagnitude = Math.max(
                MIN_LATERAL_OFFSET,
                Math.abs(breacherLateral) + BREACHER_CLEARANCE
        );
        double desiredLateral = desiredSign * desiredMagnitude;
        double lateralCorrection = desiredLateral - currentLateral;

        return baseDestination.add(right.scale(lateralCorrection));
    }

    private SwarmFireSupportLanePolicy() {}
}
