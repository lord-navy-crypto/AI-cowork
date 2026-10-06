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
        double preferredSign = Math.floorMod(formationSlot, 2) == 0 ? 1.0 : -1.0;
        return applyWithPreferredSign(
                baseDestination,
                target,
                targetForward,
                preferredSign,
                breacherPositions
        );
    }

    public static SwarmCombatPlanner.Vec2 applyWithPreferredSign(
            SwarmCombatPlanner.Vec2 baseDestination,
            SwarmCombatPlanner.Vec2 target,
            SwarmCombatPlanner.Vec2 targetForward,
            double preferredSign,
            List<SwarmCombatPlanner.Vec2> breacherPositions
    ) {
        if (baseDestination == null
                || target == null
                || targetForward == null
                || breacherPositions == null
                || breacherPositions.isEmpty()) {
            return baseDestination;
        }

        double centroidX = 0.0;
        double centroidZ = 0.0;
        int samples = 0;
        for (SwarmCombatPlanner.Vec2 breacher : breacherPositions) {
            if (breacher == null) {
                continue;
            }
            centroidX += breacher.x();
            centroidZ += breacher.z();
            samples++;
        }

        if (samples == 0) {
            return baseDestination;
        }

        SwarmCombatPlanner.Vec2 breacherCentroid =
                new SwarmCombatPlanner.Vec2(centroidX / samples, centroidZ / samples);

        // Prefer the real target-to-breacher ingress axis. Player facing is only
        // a fallback when the breacher is effectively on top of the target.
        SwarmCombatPlanner.Vec2 ingress = breacherCentroid.subtract(target).normalized();
        if (ingress.length() < EPS) {
            ingress = targetForward.normalized();
        }
        if (ingress.length() < EPS) {
            ingress = new SwarmCombatPlanner.Vec2(0.0, 1.0);
        }

        SwarmCombatPlanner.Vec2 right = new SwarmCombatPlanner.Vec2(ingress.z(), -ingress.x());
        double desiredSign = preferredSign >= 0.0 ? 1.0 : -1.0;

        SwarmCombatPlanner.Vec2 destinationRelative = baseDestination.subtract(target);
        double currentLateral =
                destinationRelative.x() * right.x() + destinationRelative.z() * right.z();

        double desiredMagnitude = MIN_LATERAL_OFFSET + BREACHER_CLEARANCE;
        double desiredLateral = desiredSign * desiredMagnitude;
        double lateralCorrection = desiredLateral - currentLateral;

        return baseDestination.add(right.scale(lateralCorrection));
    }

    public static double choosePreferredSign(
            int formationSlot,
            boolean positiveLaneClear,
            boolean negativeLaneClear
    ) {
        if (positiveLaneClear && !negativeLaneClear) {
            return 1.0;
        }
        if (!positiveLaneClear && negativeLaneClear) {
            return -1.0;
        }
        return Math.floorMod(formationSlot, 2) == 0 ? 1.0 : -1.0;
    }

    private SwarmFireSupportLanePolicy() {}
}
