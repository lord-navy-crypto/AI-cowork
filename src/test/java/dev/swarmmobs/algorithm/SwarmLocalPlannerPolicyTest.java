package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwarmLocalPlannerPolicyTest {

    @Test
    void rejectsBlockedCandidates() {
        Vec2 self = new Vec2(0.0, 0.0);
        Vec2 destination = new Vec2(10.0, 0.0);

        var choice = SwarmLocalPlannerPolicy.choose(
                self,
                destination,
                List.of(
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(2.0, 2.0), true, 2.0, 0.0),
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(2.0, -2.0), false, -2.0, 0.0)
                ),
                1.0,
                0.2,
                0.5
        );

        assertTrue(choice.active());
        assertEquals(1, choice.candidateIndex());
    }

    @Test
    void prefersLowerCongestionWhenProgressIsSimilar() {
        Vec2 self = new Vec2(0.0, 0.0);
        Vec2 destination = new Vec2(10.0, 0.0);

        var choice = SwarmLocalPlannerPolicy.choose(
                self,
                destination,
                List.of(
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(2.0, 2.0), false, 2.0, 3.0),
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(2.0, -2.0), false, -2.0, 0.2)
                ),
                1.0,
                0.2,
                1.0
        );

        assertEquals(1, choice.candidateIndex());
    }

    @Test
    void prefersForwardProgressWhenCongestionIsEqual() {
        Vec2 self = new Vec2(0.0, 0.0);
        Vec2 destination = new Vec2(10.0, 0.0);

        var choice = SwarmLocalPlannerPolicy.choose(
                self,
                destination,
                List.of(
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(1.0, 2.0), false, 2.0, 0.0),
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(3.0, -2.0), false, -2.0, 0.0)
                ),
                1.0,
                0.1,
                0.0
        );

        assertEquals(1, choice.candidateIndex());
    }

    @Test
    void allBlockedFallsBackToDestination() {
        Vec2 destination = new Vec2(8.0, 0.0);
        var choice = SwarmLocalPlannerPolicy.choose(
                new Vec2(0.0, 0.0),
                destination,
                List.of(
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(2.0, 1.0), true, 1.0, 0.0)
                ),
                1.0,
                1.0,
                1.0
        );

        assertFalse(choice.active());
        assertEquals(destination, choice.waypoint());
    }

    @Test
    void deterministicTieKeepsEarlierCandidate() {
        Vec2 self = new Vec2(0.0, 0.0);
        Vec2 destination = new Vec2(10.0, 0.0);

        var choice = SwarmLocalPlannerPolicy.choose(
                self,
                destination,
                List.of(
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(2.0, 2.0), false, 2.0, 0.0),
                        new SwarmLocalPlannerPolicy.Candidate(new Vec2(2.0, -2.0), false, -2.0, 0.0)
                ),
                1.0,
                0.2,
                0.0
        );

        assertEquals(0, choice.candidateIndex());
    }
}
