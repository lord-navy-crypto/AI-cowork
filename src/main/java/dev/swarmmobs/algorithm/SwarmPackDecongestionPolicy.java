package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import java.util.List;

/**
 * Local Minecraft waypoint selection to keep crowded Zombie packs from all
 * trying the same square. Only positions of nearby same-target mobs and an
 * already observed target direction are used. No new entity scans.
 */
public final class SwarmPackDecongestionPolicy {
    public static final double DIVERSION_BLOCKS = 2.5;
    public static final double NEAR_PEER_BLOCKS = 3.0;
    public static final double LANE_CLEARANCE_BLOCKS = 1.75;

    public record Choice(int side, boolean active, int leftOccupancy,
                         int rightOccupancy) {}

    public static Choice choose(Vec2 self, Vec2 baseDestination, Vec2 forward,
                                List<Vec2> sameTargetPeers, SwarmRole role,
                                SwarmAgentArchetype archetype, int stableSlot,
                                int previousSide, boolean eligible) {
        Choice no = new Choice(0, false, 0, 0);
        if (!eligible || archetype != SwarmAgentArchetype.ASSAULT
                || role != SwarmRole.CHASER
                || !finite(self) || !finite(baseDestination) || !finite(forward)
                || forward.length() < 0.01 || sameTargetPeers == null
                || sameTargetPeers.size() < 3) {
            return no;
        }
        int nearby = 0;
        for (Vec2 peer : sameTargetPeers) {
            if (finite(peer) && self.subtract(peer).length() < NEAR_PEER_BLOCKS) {
                nearby++;
            }
        }
        if (nearby < 2) return no;

        Vec2 direction = forward.normalized();
        Vec2 right = new Vec2(direction.z(), -direction.x());
        Vec2 plus = baseDestination.add(right.scale(DIVERSION_BLOCKS));
        Vec2 minus = baseDestination.add(right.scale(-DIVERSION_BLOCKS));
        int rightCount = countNear(plus, sameTargetPeers);
        int leftCount = countNear(minus, sameTargetPeers);

        // Stable preference requires a measurable difference of at least two
        // other mobs before an already selected lane may switch.
        int side;
        if (previousSide > 0 && rightCount <= leftCount + 1) side = 1;
        else if (previousSide < 0 && leftCount <= rightCount + 1) side = -1;
        else if (rightCount < leftCount) side = 1;
        else if (leftCount < rightCount) side = -1;
        else side = Math.floorMod(stableSlot, 2) == 0 ? 1 : -1;
        return new Choice(side, true, leftCount, rightCount);
    }

    public static Vec2 waypoint(Vec2 base, Vec2 forward, int side) {
        if (!finite(base) || !finite(forward) || forward.length() < 0.01
                || side == 0) return base;
        Vec2 unit = forward.normalized();
        Vec2 right = new Vec2(unit.z(), -unit.x());
        return base.add(right.scale((side > 0 ? 1.0 : -1.0) * DIVERSION_BLOCKS));
    }

    private static int countNear(Vec2 waypoint, List<Vec2> peers) {
        int count = 0;
        for (Vec2 peer : peers) {
            if (finite(peer) && peer.subtract(waypoint).length()
                    < LANE_CLEARANCE_BLOCKS) count++;
        }
        return count;
    }

    private static boolean finite(Vec2 v) {
        return v != null && Double.isFinite(v.x()) && Double.isFinite(v.z());
    }

    private SwarmPackDecongestionPolicy() {}
}
