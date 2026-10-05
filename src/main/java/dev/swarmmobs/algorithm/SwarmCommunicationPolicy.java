package dev.swarmmobs.algorithm;

import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic communication impairment model for swarm target messages.
 *
 * The model deliberately avoids java.util.Random so identical sender/receiver/message
 * inputs and the same experiment seed always produce the same delivery decision.
 */
public final class SwarmCommunicationPolicy {

    public record TargetMessage(
            UUID senderId,
            TargetObservation observation,
            long sentTick,
            long deliverTick
    ) {
        public UUID targetId() {
            return observation.targetId();
        }

        public long observationTick() {
            return observation.observationTick();
        }
    }

    public static Optional<TargetMessage> maybeTransmit(
            UUID senderId,
            UUID receiverId,
            TargetObservation observation,
            long sentTick,
            int latencyTicks,
            double packetDropRate,
            int experimentSeed
    ) {
        if (senderId == null || receiverId == null || observation == null) {
            return Optional.empty();
        }

        double dropRate = Math.max(0.0, Math.min(1.0, packetDropRate));
        if (dropRate >= 1.0) {
            return Optional.empty();
        }

        if (dropRate > 0.0) {
            double sample = deterministicUnitInterval(
                    senderId,
                    receiverId,
                    observation.targetId(),
                    observation.observationTick(),
                    sentTick,
                    experimentSeed
            );
            if (sample < dropRate) {
                return Optional.empty();
            }
        }

        long deliverTick = sentTick + Math.max(0, latencyTicks);
        return Optional.of(new TargetMessage(
                senderId,
                observation,
                sentTick,
                deliverTick
        ));
    }

    public static boolean withinRange(double distanceSquared, double communicationRadius) {
        if (communicationRadius <= 0.0) {
            return false;
        }
        return distanceSquared <= communicationRadius * communicationRadius;
    }

    static double deterministicUnitInterval(
            UUID senderId,
            UUID receiverId,
            UUID targetId,
            long observationTick,
            long sentTick,
            int experimentSeed
    ) {
        long value = senderId.getMostSignificantBits()
                ^ Long.rotateLeft(senderId.getLeastSignificantBits(), 7)
                ^ Long.rotateLeft(receiverId.getMostSignificantBits(), 17)
                ^ Long.rotateLeft(receiverId.getLeastSignificantBits(), 29)
                ^ Long.rotateLeft(targetId.getMostSignificantBits(), 41)
                ^ targetId.getLeastSignificantBits()
                ^ observationTick * 0x9E3779B97F4A7C15L
                ^ sentTick * 0xD1B54A32D192ED03L
                ^ ((long) experimentSeed * 0x94D049BB133111EBL);

        long mixed = mix64(value);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private SwarmCommunicationPolicy() {}
}
