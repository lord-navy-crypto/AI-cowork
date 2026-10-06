package dev.swarmmobs.algorithm;

import java.util.UUID;

/**
 * Deterministic fault-injection policy for direct target sensing.
 *
 * The policy intentionally avoids Random so repeated experiments with the same
 * agent, target, observation tick, and seed reproduce the same dropout/noise.
 */
public final class SwarmSensingPolicy {

    public record Sample(
            boolean observed,
            double measuredX,
            double measuredY,
            double measuredZ,
            double horizontalNoiseMagnitude
    ) {}

    public static Sample samplePosition(
            UUID observerId,
            UUID targetId,
            long observationTick,
            double trueX,
            double trueY,
            double trueZ,
            double dropoutRate,
            double maxHorizontalNoise,
            int experimentSeed
    ) {
        if (observerId == null || targetId == null) {
            return new Sample(false, trueX, trueY, trueZ, 0.0);
        }

        double clampedDrop = clamp(dropoutRate, 0.0, 1.0);
        if (clampedDrop >= 1.0) {
            return new Sample(false, trueX, trueY, trueZ, 0.0);
        }

        double dropoutSample = deterministicUnitInterval(
                observerId,
                targetId,
                observationTick,
                experimentSeed,
                0x6A09E667F3BCC909L
        );
        if (dropoutSample < clampedDrop) {
            return new Sample(false, trueX, trueY, trueZ, 0.0);
        }

        double noiseLimit = Math.max(0.0, maxHorizontalNoise);
        if (noiseLimit <= 0.0) {
            return new Sample(true, trueX, trueY, trueZ, 0.0);
        }

        double nx = signedUnit(
                observerId,
                targetId,
                observationTick,
                experimentSeed,
                0xBB67AE8584CAA73BL
        ) * noiseLimit;
        double nz = signedUnit(
                observerId,
                targetId,
                observationTick,
                experimentSeed,
                0x3C6EF372FE94F82BL
        ) * noiseLimit;

        return new Sample(
                true,
                trueX + nx,
                trueY,
                trueZ + nz,
                Math.hypot(nx, nz)
        );
    }

    private static double signedUnit(
            UUID observerId,
            UUID targetId,
            long observationTick,
            int experimentSeed,
            long salt
    ) {
        return deterministicUnitInterval(
                observerId,
                targetId,
                observationTick,
                experimentSeed,
                salt
        ) * 2.0 - 1.0;
    }

    static double deterministicUnitInterval(
            UUID observerId,
            UUID targetId,
            long observationTick,
            int experimentSeed,
            long salt
    ) {
        long value = observerId.getMostSignificantBits()
                ^ Long.rotateLeft(observerId.getLeastSignificantBits(), 11)
                ^ Long.rotateLeft(targetId.getMostSignificantBits(), 23)
                ^ Long.rotateLeft(targetId.getLeastSignificantBits(), 37)
                ^ observationTick * 0x9E3779B97F4A7C15L
                ^ ((long) experimentSeed * 0xD1B54A32D192ED03L)
                ^ salt;

        long mixed = mix64(value);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private SwarmSensingPolicy() {}
}
