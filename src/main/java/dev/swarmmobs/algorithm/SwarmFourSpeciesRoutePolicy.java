package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import java.util.List;

/**
 * Decentralized in-game route spacing for all four vanilla Minecraft swarm
 * archetypes. This only proposes short lateral positions; the world caller
 * must validate collision, footing and, for Skeletons, a safe shot corridor.
 * Existing target observations, vanilla attacks and PathNavigation own combat.
 */
public final class SwarmFourSpeciesRoutePolicy {
    public static final double LATERAL_STEP_BLOCKS = 2.25;
    public static final double OCCUPIED_RADIUS_BLOCKS = 2.0;
    public record Options(Vec2 original, Vec2 left, Vec2 right,
            int originalOccupancy, int leftOccupancy, int rightOccupancy,
            boolean eligible) {}

    public record Choice(Vec2 destination, int side, boolean changed,
            int originalOccupancy, int finalOccupancy) {}

    public static Options propose(SwarmAgentArchetype archetype,
            Vec2 self, Vec2 planned, Vec2 observedTarget, Vec2 heading,
            List<Vec2> trafficPeers, double confidence,
            boolean directlyObserved, boolean enabled) {
        Options no = new Options(planned, planned, planned, 0, 0, 0, false);
        if (!enabled || !directlyObserved || archetype == null
                || !finite(self) || !finite(planned) || !finite(observedTarget)
                || !finite(heading) || heading.length() < 0.01
                || !Double.isFinite(confidence) || confidence < 0.70
                || trafficPeers == null || trafficPeers.isEmpty()
                || self.subtract(observedTarget).length()
                        <= minNativeAttackProtection(archetype)) {
            return no;
        }
        int base = countNear(planned, trafficPeers);
        if (base == 0) return no;

        Vec2 forward = heading.normalized();
        Vec2 side = new Vec2(-forward.z(), forward.x());
        Vec2 left = planned.add(side.scale(LATERAL_STEP_BLOCKS));
        Vec2 right = planned.add(side.scale(-LATERAL_STEP_BLOCKS));
        int l = countNear(left, trafficPeers);
        int r = countNear(right, trafficPeers);
        return new Options(planned, left, right, base, l, r,
                l < base || r < base);
    }

    /** Return the original route when terrain or occupancy cannot improve. */
    public static Choice choose(Options o, boolean leftWorldSafe,
            boolean rightWorldSafe, int previousSide, int stableSlot) {
        if (o == null) return new Choice(null, 0, false, 0, 0);
        Choice no = new Choice(o.original(), 0, false,
                o.originalOccupancy(), o.originalOccupancy());
        if (!o.eligible()) return no;
        boolean left = leftWorldSafe && o.leftOccupancy() < o.originalOccupancy();
        boolean right = rightWorldSafe && o.rightOccupancy() < o.originalOccupancy();
        if (!left && !right) return no;
        if (left && right) {
            // Previous stable lane wins a one-peer tie. Do not oscillate
            // between equally occupied candidates on every planner tick.
            int preferred;
            if (previousSide > 0 && o.leftOccupancy() <= o.rightOccupancy() + 1) {
                preferred = 1;
            } else if (previousSide < 0
                    && o.rightOccupancy() <= o.leftOccupancy() + 1) {
                preferred = -1;
            } else if (o.leftOccupancy() < o.rightOccupancy()) {
                preferred = 1;
            } else if (o.rightOccupancy() < o.leftOccupancy()) {
                preferred = -1;
            } else {
                preferred = Math.floorMod(stableSlot, 2) == 0 ? 1 : -1;
            }
            return new Choice(preferred > 0 ? o.left() : o.right(),
                    preferred, true, o.originalOccupancy(),
                    preferred > 0 ? o.leftOccupancy() : o.rightOccupancy());
        }
        return new Choice(left ? o.left() : o.right(),
                left ? 1 : -1, true, o.originalOccupancy(),
                left ? o.leftOccupancy() : o.rightOccupancy());
    }

    private static int countNear(Vec2 point, List<Vec2> peers) {
        int count = 0;
        double r2 = OCCUPIED_RADIUS_BLOCKS * OCCUPIED_RADIUS_BLOCKS;
        for (Vec2 peer : peers) {
            if (finite(peer)) {
                Vec2 delta = point.subtract(peer);
                if (delta.x() * delta.x() + delta.z() * delta.z() < r2) count++;
            }
        }
        return count;
    }

    private static double minNativeAttackProtection(SwarmAgentArchetype archetype) {
        return switch (archetype) {
            case ASSAULT, FLANKER -> 4.5;
            case RANGED_SUPPORT -> 8.0;
            case BREACHER -> 6.0;
        };
    }

    private static boolean finite(Vec2 p) {
        return p != null && Double.isFinite(p.x()) && Double.isFinite(p.z());
    }

    private SwarmFourSpeciesRoutePolicy() {}
}
