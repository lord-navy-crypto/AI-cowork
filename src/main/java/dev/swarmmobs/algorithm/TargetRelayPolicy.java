package dev.swarmmobs.algorithm;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure target-memory selection logic.
 *
 * A relay carries the entire original observation snapshot. Selecting or forwarding
 * a record never rewrites its timestamp or spatial estimate.
 */
public final class TargetRelayPolicy {

    public static Optional<TargetObservation> selectFreshest(
            long currentTick,
            int memoryTicks,
            List<TargetObservation> records
    ) {
        long maxAge = Math.max(0, memoryTicks);

        return records.stream()
                .filter(record -> record != null && record.targetId() != null)
                .filter(record -> {
                    long age = currentTick - record.observationTick();
                    return age >= 0 && age <= maxAge;
                })
                .max(Comparator.comparingLong(TargetObservation::observationTick));
    }

    private TargetRelayPolicy() {}
}
