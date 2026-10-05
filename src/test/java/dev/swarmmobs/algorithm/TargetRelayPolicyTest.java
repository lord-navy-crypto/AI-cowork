package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.TargetRelayPolicy.TargetRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TargetRelayPolicyTest {

    @Test
    void freshestValidObservationWins() {
        UUID target = UUID.randomUUID();

        TargetRecord selected = TargetRelayPolicy.selectFreshest(
                100,
                30,
                List.of(
                        new TargetRecord(target, 75),
                        new TargetRecord(target, 95),
                        new TargetRecord(target, 90)
                )
        ).orElseThrow();

        assertEquals(95, selected.observationTick());
    }

    @Test
    void expiredObservationIsRejected() {
        UUID target = UUID.randomUUID();

        assertTrue(TargetRelayPolicy.selectFreshest(
                100,
                20,
                List.of(new TargetRecord(target, 79))
        ).isEmpty());
    }

    @Test
    void futureObservationIsRejected() {
        UUID target = UUID.randomUUID();

        assertTrue(TargetRelayPolicy.selectFreshest(
                100,
                20,
                List.of(new TargetRecord(target, 101))
        ).isEmpty());
    }

    @Test
    void relayPreservesOriginalObservationTick() {
        UUID target = UUID.randomUUID();
        TargetRecord source = new TargetRecord(target, 42);

        TargetRecord selected = TargetRelayPolicy.selectFreshest(
                50,
                100,
                List.of(source)
        ).orElseThrow();

        assertEquals(42, selected.observationTick());
        assertSame(source, selected);
    }

    @Test
    void newerDifferentTargetCanReplaceOlderMemory() {
        UUID oldTarget = UUID.randomUUID();
        UUID newTarget = UUID.randomUUID();

        TargetRecord selected = TargetRelayPolicy.selectFreshest(
                200,
                100,
                List.of(
                        new TargetRecord(oldTarget, 150),
                        new TargetRecord(newTarget, 190)
                )
        ).orElseThrow();

        assertEquals(newTarget, selected.targetId());
        assertEquals(190, selected.observationTick());
    }
}
