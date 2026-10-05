package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCommunicationPolicy.TargetMessage;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmCommunicationPolicyTest {
    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECEIVER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void zeroDropRateAlwaysDelivers() {
        Optional<TargetMessage> message = SwarmCommunicationPolicy.maybeTransmit(
                SENDER, RECEIVER, observation(100L),
                110L,
                0, 0.0, 42
        );

        assertTrue(message.isPresent());
        assertEquals(110L, message.orElseThrow().deliverTick());
    }

    @Test
    void fullDropRateAlwaysDrops() {
        assertTrue(SwarmCommunicationPolicy.maybeTransmit(
                SENDER, RECEIVER, observation(100L),
                110L,
                0, 1.0, 42
        ).isEmpty());
    }

    @Test
    void latencyDelaysDeliveryWithoutChangingObservationSnapshot() {
        TargetObservation source = new TargetObservation(
                TARGET, 77L,
                12.0, 70.0, -4.0,
                0.0, 1.0
        );

        TargetMessage message = SwarmCommunicationPolicy.maybeTransmit(
                SENDER, RECEIVER, source,
                100L,
                12, 0.0, 42
        ).orElseThrow();

        assertSame(source, message.observation());
        assertEquals(77L, message.observationTick());
        assertEquals(12.0, message.observation().x(), 1.0e-9);
        assertEquals(100L, message.sentTick());
        assertEquals(112L, message.deliverTick());
    }

    @Test
    void impairmentDecisionIsDeterministicForSameInputs() {
        var first = SwarmCommunicationPolicy.maybeTransmit(
                SENDER, RECEIVER, observation(90L),
                100L,
                5, 0.45, 7
        );
        var second = SwarmCommunicationPolicy.maybeTransmit(
                SENDER, RECEIVER, observation(90L),
                100L,
                5, 0.45, 7
        );

        assertEquals(first, second);
    }

    @Test
    void communicationRangeUsesSquaredDistance() {
        assertTrue(SwarmCommunicationPolicy.withinRange(25.0, 5.0));
        assertFalse(SwarmCommunicationPolicy.withinRange(25.01, 5.0));
        assertFalse(SwarmCommunicationPolicy.withinRange(0.0, 0.0));
    }

    private static TargetObservation observation(long tick) {
        return new TargetObservation(TARGET, tick, 1.0, 64.0, 2.0, 0.0, 1.0);
    }
}
