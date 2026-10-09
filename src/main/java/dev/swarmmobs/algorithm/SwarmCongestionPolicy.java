package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import java.util.List;

/**
 * Geometry used to gather congestion peers once for a set of local candidates
 * and score each candidate against that immutable position snapshot.
 */
public final class SwarmCongestionPolicy {
    /**
     * A peer within congestionRadius of any candidate is always inside this
     * conservative scan radius around the originating mob.
     */
    public static double scanRadius(Vec2 origin, List<Vec2> candidates, double congestionRadius) {
        double furthest = 0.0;
        for (Vec2 candidate : candidates) {
            if (!Double.isFinite(candidate.x()) || !Double.isFinite(candidate.z())) {
                continue;
            }
            furthest = Math.max(furthest, Math.hypot(
                    candidate.x() - origin.x(),
                    candidate.z() - origin.z()
            ));
        }
        return Math.max(0.0, congestionRadius) + furthest;
    }

    public static int countWithin(List<Vec2> peerPositions, Vec2 candidate, double congestionRadius) {
        double radius = Math.max(0.0, congestionRadius);
        double radiusSquared = radius * radius;
        int count = 0;
        for (Vec2 peer : peerPositions) {
            double dx = peer.x() - candidate.x();
            double dz = peer.z() - candidate.z();
            if (dx * dx + dz * dz <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    private SwarmCongestionPolicy() {}
}
