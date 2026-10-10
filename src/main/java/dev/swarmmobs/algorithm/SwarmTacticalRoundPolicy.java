package dev.swarmmobs.algorithm;

import java.util.List;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;

/**
 * Measurement-based, sampled support-position controller for Minecraft swarms.
 *
 * A "round" is one observation/decision cycle, never a turn to attack.
 * The controller chooses the lowest estimated travel + clearance-deficit
 * cost from actually checked available positions. Previous side is retained
 * unless changing saves more distance than crossing one agent footprint.
 * This is a bounded kinematic proxy, NOT full path planning, MPC, or a
 * mathematical proof of collision avoidance.
 */
public final class SwarmTacticalRoundPolicy {
    public enum Phase { HOLD, COVER, ROTATE }

    public record SupportDecision(Phase phase, int side,
                                  double chosenCost, boolean clearanceVerified) {}

    /**
     * Distances and costs are Minecraft blocks, not arbitrary combat scores.
     * Hard constraints (verified terrain/shot lane and known teammate
     * obstruction) precede cost minimization.
     *
     * The switching overhead is the agent's physical width: a minimum
     * measurable displacement to justify abandoning a previously good lane.
     * If that lane becomes unsafe, the hard constraint overrides inertia.
     */
    public static SupportDecision chooseSupport(
            Vec2 self, Vec2 positive, Vec2 negative,
            List<Vec2> localPeers, double requiredSeparation,
            double agentWidth, int currentSide, int formationSlot,
            boolean positiveBlockClear, boolean negativeBlockClear,
            boolean positiveFriendlyClear, boolean negativeFriendlyClear) {

        if (!finite(self) || !finite(positive) || !finite(negative)
                || !Double.isFinite(requiredSeparation) || requiredSeparation <= 0
                || !Double.isFinite(agentWidth) || agentWidth <= 0) {
            return new SupportDecision(Phase.HOLD, 0,
                    Double.POSITIVE_INFINITY, false);
        }

        boolean plus = positiveBlockClear && positiveFriendlyClear;
        boolean minus = negativeBlockClear && negativeFriendlyClear;
        if (!plus && !minus) return new SupportDecision(Phase.HOLD, 0,
                Double.POSITIVE_INFINITY, false);

        double positiveCost = plus ? localCost(self, positive, localPeers,
                requiredSeparation) : Double.POSITIVE_INFINITY;
        double negativeCost = minus ? localCost(self, negative, localPeers,
                requiredSeparation) : Double.POSITIVE_INFINITY;

        int previous = Integer.compare(currentSide, 0);
        int selected;
        if (plus && !minus) selected = +1;
        else if (minus && !plus) selected = -1;
        else if (previous > 0 && positiveCost <= negativeCost + agentWidth)
            selected = +1;
        else if (previous < 0 && negativeCost <= positiveCost + agentWidth)
            selected = -1;
        else if (positiveCost < negativeCost) selected = +1;
        else if (negativeCost < positiveCost) selected = -1;
        else selected = Math.floorMod(formationSlot, 2) == 0 ? +1 : -1;

        boolean rotated = previous != 0 && previous != selected;
        return new SupportDecision(rotated ? Phase.ROTATE : Phase.COVER,
                selected, selected > 0 ? positiveCost : negativeCost, true);
    }

    /**
     * A geometric one-step objective: locomotion to the proposed waypoint,
     * plus the sum of predicted violations of the configured separation
     * distance at that waypoint. No synthetic reward, damage or target risk.
     */
    public static double localCost(Vec2 self, Vec2 candidate,
                                   List<Vec2> localPeers, double separation) {
        if (!finite(self) || !finite(candidate) || !Double.isFinite(separation)
                || separation <= 0) return Double.POSITIVE_INFINITY;
        double cost = self.subtract(candidate).length();
        if (localPeers != null) for (Vec2 peer : localPeers) {
            if (!finite(peer)) continue;
            cost += Math.max(0.0,separation-candidate.subtract(peer).length());
        }
        return cost;
    }

    /** Non-ranged agents keep the ordinary swarm planner; no invented maneuver. */
    public static Phase classification(boolean active, boolean supportAndFrontline) {
        return active && supportAndFrontline ? Phase.COVER : Phase.HOLD;
    }

    private static boolean finite(Vec2 v) {
        return v != null && Double.isFinite(v.x()) && Double.isFinite(v.z());
    }

    private SwarmTacticalRoundPolicy() {}
}
