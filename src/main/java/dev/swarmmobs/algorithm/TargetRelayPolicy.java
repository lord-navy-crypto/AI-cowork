package dev.swarmmobs.algorithm;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure target-memory selection logic.
 *
 * A relay carries the original observation tick. Selecting or forwarding a record
 * never rewrites that tick, which prevents stale information from becoming fresh
 * just because it traveled through another swarm member.
 */
public final class TargetRelayPolicy {

    public record TargetRecord(UUID targetId, long observationTick) {}

    public static Optional<TargetRecord> selectFreshest(
            long currentTick,
            int memoryTicks,
            List<TargetRecord> records
    ) {
        long maxAge = Math.max(0, memoryTicks);

        return records.stream()
                .filter(record -> record != null && record.targetId() != null)
                .filter(record -> {
                    long age = currentTick - record.observationTick();
                    return age >= 0 && age <= maxAge;
                })
                .max(Comparator.comparingLong(TargetRecord::observationTick));
    }

    private TargetRelayPolicy() {}
}
