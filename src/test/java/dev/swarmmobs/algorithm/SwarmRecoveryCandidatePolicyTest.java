package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmRecoveryCandidatePolicyTest {

    @Test
    void generatesSixSymmetricRecoveryOptions() {
        var candidates = SwarmRecoveryCandidatePolicy.generate(
                new Vec2(0.0, 0.0),
                new Vec2(10.0, 0.0),
                2.0
        );

        assertEquals(6, candidates.size());

        assertEquals(1.5, candidates.get(0).waypoint().x(), 1.0e-9);
        assertEquals(2.0, candidates.get(0).waypoint().z(), 1.0e-9);

        assertEquals(1.5, candidates.get(1).waypoint().x(), 1.0e-9);
        assertEquals(-2.0, candidates.get(1).waypoint().z(), 1.0e-9);

        assertEquals(0.0, candidates.get(2).waypoint().x(), 1.0e-9);
        assertEquals(3.0, candidates.get(2).waypoint().z(), 1.0e-9);

        assertEquals(-1.0, candidates.get(4).waypoint().x(), 1.0e-9);
        assertEquals(2.0, candidates.get(4).waypoint().z(), 1.0e-9);
    }

    @Test
    void disabledOrDegenerateRecoveryProducesNoCandidates() {
        assertTrue(SwarmRecoveryCandidatePolicy.generate(
                new Vec2(0.0, 0.0),
                new Vec2(10.0, 0.0),
                0.0
        ).isEmpty());

        assertTrue(SwarmRecoveryCandidatePolicy.generate(
                new Vec2(3.0, 4.0),
                new Vec2(3.0, 4.0),
                2.0
        ).isEmpty());
    }

    @Test
    void candidatesRotateWithDestinationDirection() {
        var candidates = SwarmRecoveryCandidatePolicy.generate(
                new Vec2(0.0, 0.0),
                new Vec2(0.0, 10.0),
                2.0
        );

        assertEquals(-2.0, candidates.get(0).waypoint().x(), 1.0e-9);
        assertEquals(1.5, candidates.get(0).waypoint().z(), 1.0e-9);
    }
}
