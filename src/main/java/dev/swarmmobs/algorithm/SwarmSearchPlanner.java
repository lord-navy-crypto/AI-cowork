package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

import java.util.List;

/**
 * Lightweight decentralized search around the last-known target position.
 *
 * Search expands as confidence decays. Stable slots separate local agents into
 * deterministic sectors, and the sector rotates in discrete phases so a stale
 * target produces coverage instead of a static ring.
 */
public final class SwarmSearchPlanner {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double ANGLE_STEP = Math.PI / 6.0;

    public record SearchPlan(
            Vec2 destination,
            double searchRadius,
            double angleRadians,
            double separationMagnitude,
            double cohesionMagnitude,
            double alignmentMagnitude,
            double steeringMagnitude
    ) {}

    public static SearchPlan planWithMotion(
            SwarmAgentArchetype archetype,
            int stableSlot,
            Vec2 self,
            Vec2 selfVelocity,
            Vec2 lastKnownTarget,
            double confidence,
            long targetAgeTicks,
            List<Vec2> neighborPositions,
            List<Vec2> neighborVelocities,
            int sectorCount,
            double minRadius,
            double maxRadius,
            int phaseTicks,
            double separationRadius,
            double separationWeight,
            double cohesionWeight,
            double alignmentWeight,
            double maxSteeringCorrection
    ) {
        double normalizedConfidence = clamp(confidence, 0.0, 1.0);
        double uncertainty = 1.0 - normalizedConfidence;

        double baseRadius = lerp(
                Math.max(0.5, minRadius),
                Math.max(minRadius, maxRadius),
                uncertainty
        );

        double archetypeMultiplier = switch (archetype) {
            case ASSAULT -> 0.55;
            case RANGED_SUPPORT -> 1.25;
            case FLANKER -> 1.0;
        };

        double searchRadius = baseRadius * archetypeMultiplier;
        int sectors = Math.max(1, sectorCount);
        int slot = Math.floorMod(stableSlot, sectors);

        double baseAngle = TWO_PI * slot / sectors;
        long phase = Math.max(0L, targetAgeTicks) / Math.max(1, phaseTicks);

        // All agents of the same capability rotate in the same direction so their
        // sector spacing remains invariant. Different capability bands may rotate
        // in opposite directions, improving coverage without collapsing same-band
        // agents onto one destination.
        double direction = switch (archetype) {
            case ASSAULT -> 1.0;
            case RANGED_SUPPORT -> 1.0;
            case FLANKER -> -1.0;
        };

        double angle = baseAngle + direction * phase * ANGLE_STEP;

        Vec2 baseDestination = lastKnownTarget.add(
                new Vec2(Math.cos(angle), Math.sin(angle)).scale(searchRadius)
        );

        Vec2 separation = SwarmCombatPlanner.separation(
                self,
                neighborPositions,
                separationRadius
        );
        Vec2 cohesion = SwarmCombatPlanner.cohesion(self, neighborPositions);
        Vec2 alignment = SwarmCombatPlanner.alignment(
                selfVelocity,
                neighborVelocities
        );

        Vec2 rawSteering = separation.scale(separationWeight)
                .add(cohesion.scale(cohesionWeight))
                .add(alignment.scale(alignmentWeight));

        Vec2 steering = SwarmCombatPlanner.clampLength(
                rawSteering,
                maxSteeringCorrection
        );

        return new SearchPlan(
                baseDestination.add(steering),
                searchRadius,
                angle,
                separation.length(),
                cohesion.length(),
                alignment.length(),
                steering.length()
        );
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp(t, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private SwarmSearchPlanner() {}
}
