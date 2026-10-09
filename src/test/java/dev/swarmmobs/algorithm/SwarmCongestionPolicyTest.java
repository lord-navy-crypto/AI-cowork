package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmCongestionPolicyTest {
    @Test
    void scanEnvelopeIncludesPeersNearMostDistantCandidate() {
        Vec2 origin = new Vec2(0.0, 0.0);
        List<Vec2> candidates = List.of(
                new Vec2(3.0, 4.0),
                new Vec2(-12.0, 5.0)
        );
        // max distance from origin is 13; add 2.5 congestion range.
        assertEquals(15.5, SwarmCongestionPolicy.scanRadius(
                origin, candidates, 2.5
        ), 1.0e-9);
    }

    @Test
    void reusedPeerSnapshotCountsEachCandidateIndependently() {
        List<Vec2> peers = List.of(
                new Vec2(1.0, 0.0),
                new Vec2(2.0, 0.0),
                new Vec2(10.0, 10.0),
                new Vec2(12.0, 10.0)
        );

        assertEquals(2, SwarmCongestionPolicy.countWithin(
                peers, new Vec2(0.0, 0.0), 2.0
        ));
        assertEquals(2, SwarmCongestionPolicy.countWithin(
                peers, new Vec2(11.0, 10.0), 1.0
        ));
        assertEquals(0, SwarmCongestionPolicy.countWithin(
                peers, new Vec2(50.0, 50.0), 3.0
        ));
    }

    @Test
    void emptyCandidatesHaveMinimumScanRadius() {
        assertEquals(4.0, SwarmCongestionPolicy.scanRadius(
                new Vec2(9.0, 1.0), List.of(), 4.0
        ), 1.0e-9);
    }
}
