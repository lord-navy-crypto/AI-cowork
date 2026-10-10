package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import java.util.List;

/**
 * Decentralized rejoining during Minecraft's stale-target search.
 *
 * Never fetches the live target or creates a squad leader. Nearby allies
 * must already share the same observed target; this policy only nudges one
 * search waypoint toward their physical, locally observed centroid.
 */
public final class SwarmSearchRallyPolicy {
    public static final double MIN_SEPARATION = 7.0;
    public static final double MAX_MEMORY_REGION = 12.0;
    public static final double MAX_STEERING = 2.5;

    public record Result(Vec2 destination, boolean regrouping, int peerCount) {}

    public static Result adjust(Vec2 self, Vec2 originalDestination,
                                Vec2 lastObservedTarget,
                                List<Vec2> sameTargetPeers, boolean enabled) {
        Result unchanged = new Result(originalDestination, false, 0);
        if (!enabled || !finite(self) || !finite(originalDestination)
                || !finite(lastObservedTarget) || sameTargetPeers == null
                || sameTargetPeers.size() < 2) {
            return unchanged;
        }

        // Limit CPU work even if a plugin hands the planner a huge list.
        double sx = 0, sz = 0;
        int count = 0;
        for (Vec2 peer : sameTargetPeers) {
            if (!finite(peer)) continue;
            if (peer.subtract(lastObservedTarget).length() > MAX_MEMORY_REGION) continue;
            sx += peer.x();
            sz += peer.z();
            if (++count >= 16) break;
        }
        if (count < 2) return unchanged;

        Vec2 center = new Vec2(sx / count, sz / count);
        Vec2 toCenter = center.subtract(self);
        double separation = toCenter.length();
        if (separation < MIN_SEPARATION) {
            return new Result(originalDestination, false, count);
        }
        // Preserve the rotating sector search; correct at most 2.5 blocks.
        // This is never a new target sighting, and cannot revive an expired
        // observation. The caller uses it only within SEARCH behavior.
        double correction = Math.min(MAX_STEERING,
                (separation - MIN_SEPARATION) * 0.5);
        return new Result(originalDestination.add(
                toCenter.scale(correction / separation)), true, count);
    }

    private static boolean finite(Vec2 p) {
        return p != null && Double.isFinite(p.x()) && Double.isFinite(p.z());
    }

    private SwarmSearchRallyPolicy() {}
}
