package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import java.util.List;

/**
 * Small, observation-bound adaptive Minecraft encounter geometry.
 *
 * No "omniscient" player-position reads, new weapon behaviour, artificial
 * combat turns or global squad leader. Works with the ordinary target relay,
 * existing bounded planner and vanilla attack handoff.
 */
public final class SwarmAdaptiveTacticsPolicy {
    public enum Pattern { STANDARD, SWEEP, SURROUND }

    public record Frame(Pattern pattern, Vec2 forward, double leadBlocks) {}

    /** The observation is the ONLY source of target heading/speed. */
    public static Frame choose(
            TargetObservation observation,
            long gameTick,
            double confidence,
            List<Vec2> sameTargetPeers,
            boolean enabled
    ) {
        Vec2 facing = facing(observation);
        Frame standard = new Frame(Pattern.STANDARD, facing, 0.0);
        if (!enabled || observation == null || !observation.hasFinitePosition()
                || !Double.isFinite(confidence) || confidence < 0.65
                || gameTick < observation.observationTick()
                || gameTick - observation.observationTick() > 12
                || sameTargetPeers == null || sameTargetPeers.size() < 2) {
            return standard;
        }

        // A coherent directional motion cue matters more than which way the
        // Minecraft Player happens to point their camera.
        double speed = observation.horizontalSpeed();
        if (observation.hasFiniteVelocity()
                && speed >= 0.07 && speed <= 0.48) {
            Vec2 motion = new Vec2(observation.velocityX() / speed,
                    observation.velocityZ() / speed);
            // Bounded lead offsets are spatial waypoints, not new observations.
            return new Frame(Pattern.SWEEP, motion, Math.min(2.0, speed * 8.0));
        }

        // With a largely stationary observed player and >=3 locally observed
        // same-target members, use the pack's actual approach direction.
        // The team centroid is a local physical measurement, not a command HQ.
        double x = 0.0, z = 0.0;
        int count = 0;
        for (Vec2 peer : sameTargetPeers) {
            if (finite(peer)) {
                x += peer.x();
                z += peer.z();
                count++;
            }
        }
        if (count < 2) return standard;
        Vec2 approach = new Vec2(
                observation.x() - x / count,
                observation.z() - z / count);
        double length = approach.length();
        if (length < 1.0) return standard;
        return new Frame(Pattern.SURROUND, approach.scale(1.0 / length), 0.0);
    }

    /** Move only confirmed flank-position waypoints; never modify combat targets. */
    public static Vec2 refineDestination(Frame frame, Vec2 original,
            SwarmRole role, SwarmAgentArchetype archetype) {
        if (frame == null || !finite(original) || frame.pattern() != Pattern.SWEEP
                || (role != SwarmRole.FLANK_LEFT && role != SwarmRole.FLANK_RIGHT)
                || !finite(frame.forward())
                || !Double.isFinite(frame.leadBlocks())
                || frame.leadBlocks() <= 0.0) {
            return original;
        }
        // The climbing Spider has more lateral freedom than the melee Zombie.
        // Skeletons keep their verified ranged lane; Creepers keep their
        // vanilla fuse/handoff corridor.
        double factor = switch (archetype) {
            case FLANKER -> 1.0;
            case ASSAULT -> 0.5;
            default -> 0.0;
        };
        return original.add(frame.forward().scale(
                Math.min(2.0, frame.leadBlocks()) * factor));
    }

    /**
     * An otherwise rearward ASSAULT game slot can take a small waypoint on
     * the far side of a stationary target during SURROUND. This yields an
     * actual different approach corridor, not merely a new tactical label.
     * Vanilla block/path validation and normal melee handoff still apply.
     */
    public static Vec2 farSideWaypoint(Frame frame, Vec2 original,
            Vec2 observedTarget, double formationRadius,
            SwarmRole role, SwarmAgentArchetype archetype) {
        if (frame == null || frame.pattern() != Pattern.SURROUND
                || role != SwarmRole.REAR_PRESSURE
                || archetype != SwarmAgentArchetype.ASSAULT
                || !finite(original) || !finite(observedTarget)
                || !finite(frame.forward())
                || !Double.isFinite(formationRadius)
                || formationRadius <= 0.0) {
            return original;
        }
        // Half the ordinary formation radius, capped at three game blocks
        // so the route cannot demand a deep arbitrary detour.
        double offset = Math.min(3.0, 0.5 * formationRadius);
        return observedTarget.add(frame.forward().scale(offset));
    }

    private static Vec2 facing(TargetObservation observation) {
        if (observation != null
                && Double.isFinite(observation.forwardX())
                && Double.isFinite(observation.forwardZ())) {
            Vec2 raw = new Vec2(observation.forwardX(), observation.forwardZ());
            if (raw.length() > 1.0e-6) return raw.normalized();
        }
        return new Vec2(0.0, 1.0);
    }

    private static boolean finite(Vec2 v) {
        return v != null && Double.isFinite(v.x()) && Double.isFinite(v.z());
    }

    private SwarmAdaptiveTacticsPolicy() {}
}
