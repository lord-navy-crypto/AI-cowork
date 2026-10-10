package dev.swarmmobs.algorithm;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lightweight per-dimension counters; server thread only.
 * These are diagnostic counts, NOT measured CPU/MSPT improvements.
 */
public final class SwarmNavigationCommandTelemetry {
    public record Snapshot(long issued, long skipped, long retryDone, long periodicRefresh) {}

    private static final class Counters {
        long issued;
        long skipped;
        long retryDone;
        long periodicRefresh;

        Snapshot snapshot() {
            return new Snapshot(issued, skipped, retryDone, periodicRefresh);
        }
    }

    private static final Map<ServerLevel, Counters> BY_LEVEL = new WeakHashMap<>();

    public static void record(ServerLevel level, SwarmNavigationCommandPolicy.Decision decision) {
        Counters counters = BY_LEVEL.computeIfAbsent(level, unused -> new Counters());
        if (decision == SwarmNavigationCommandPolicy.Decision.SKIP) {
            counters.skipped++;
        } else {
            counters.issued++;
            if (decision == SwarmNavigationCommandPolicy.Decision.RETRY_DONE) {
                counters.retryDone++;
            } else if (decision == SwarmNavigationCommandPolicy.Decision.REFRESH_ACTIVE) {
                counters.periodicRefresh++;
            }
        }
    }

    public static Snapshot snapshot(ServerLevel level) {
        Counters counters = BY_LEVEL.get(level);
        return counters == null ? new Snapshot(0L, 0L, 0L, 0L) : counters.snapshot();
    }

    private SwarmNavigationCommandTelemetry() {}
}
