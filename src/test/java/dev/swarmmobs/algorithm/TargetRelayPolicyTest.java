package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TargetRelayPolicyTest {

    @Test
    void freshestValidObservationWins() {
        UUID target = UUID.randomUUID();

        TargetObservation selected = TargetRelayPolicy.selectFreshest(
                100,
                30,
                List.of(
                        observation(target, 75, 1.0),
                        observation(target, 95, 2.0),
                        observation(target, 90, 3.0)
                )
        ).orElseThrow();

        assertEquals(95, selected.observationTick());
        assertEquals(2.0, selected.x(), 1.0e-9);
    }

    @Test
    void expiredObservationIsRejected() {
        UUID target = UUID.randomUUID();

        assertTrue(TargetRelayPolicy.selectFreshest(
                100,
                20,
                List.of(observation(target, 79, 1.0))
        ).isEmpty());
    }

    @Test
    void futureObservationIsRejected() {
        UUID target = UUID.randomUUID();

        assertTrue(TargetRelayPolicy.selectFreshest(
                100,
                20,
                List.of(observation(target, 101, 1.0))
        ).isEmpty());
    }

    @Test
    void relayPreservesOriginalSpatialSnapshot() {
        UUID target = UUID.randomUUID();
        TargetObservation source = new TargetObservation(
                target,
                42,
                7.5,
                64.0,
                -3.0,
                1.0,
                0.0
        );

        TargetObservation selected = TargetRelayPolicy.selectFreshest(
                50,
                100,
                List.of(source)
        ).orElseThrow();

        assertSame(source, selected);
        assertEquals(42, selected.observationTick());
        assertEquals(7.5, selected.x(), 1.0e-9);
        assertEquals(-3.0, selected.z(), 1.0e-9);
    }

    @Test
    void newerDifferentTargetCanReplaceOlderMemory() {
        UUID oldTarget = UUID.randomUUID();
        UUID newTarget = UUID.randomUUID();

        TargetObservation selected = TargetRelayPolicy.selectFreshest(
                200,
                100,
                List.of(
                        observation(oldTarget, 150, 1.0),
                        observation(newTarget, 190, 2.0)
                )
        ).orElseThrow();

        assertEquals(newTarget, selected.targetId());
        assertEquals(190, selected.observationTick());
    }

    private static TargetObservation observation(UUID target, long tick, double x) {
        return new TargetObservation(target, tick, x, 64.0, 0.0, 0.0, 1.0);
    }
}
