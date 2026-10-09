package dev.swarmmobs.algorithm;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One logical-server-level path-evidence budget.
 *
 * Reservations are atomic per local planning episode: a denied request must
 * defer the entire episode, never treat unmeasured paths as reachable or
 * unreachable. The FIFO waitlist makes later requests yield to previously
 * deferred, still-active agents. Minecraft world access stays on the server
 * thread; this class does not run pathfinding asynchronously.
 */
public final class SwarmPathQueryBudget {
    private static final long WAITING_TIMEOUT_TICKS = 120L;

    public record Snapshot(int tokenLimit, int reservedTokens, int waiters,
                           long reservationsGranted, long reservationsDeferred) {}

    private long currentTick = Long.MIN_VALUE;
    private int remainingTokens;
    private int tokenLimit;
    private long granted;
    private long deferred;
    private final LinkedHashMap<UUID, Long> waiting = new LinkedHashMap<>();

    public boolean tryReserve(UUID requester, long tick, int requestedTokens, int maxTokensPerTick) {
        if (requester == null || requestedTokens < 0 || maxTokensPerTick < 0) {
            throw new IllegalArgumentException("Invalid path evidence reservation");
        }

        refresh(tick, maxTokensPerTick);
        if (requestedTokens == 0) {
            return true;
        }
        if (requestedTokens > tokenLimit) {
            // Configuration should always admit our largest planning episode.
            // Do not enqueue a request that can never be granted.
            deferred++;
            return false;
        }

        // Once a request is deferred, subsequent new arrivals must not jump
        // the queue. Stale head entries expire when they stop requesting.
        UUID head = waiting.isEmpty() ? null : waiting.keySet().iterator().next();
        if (head != null && !head.equals(requester)) {
            waiting.put(requester, tick);
            deferred++;
            return false;
        }

        if (remainingTokens < requestedTokens) {
            waiting.put(requester, tick);
            deferred++;
            return false;
        }

        remainingTokens -= requestedTokens;
        waiting.remove(requester);
        granted++;
        return true;
    }

    public Snapshot snapshot(long tick, int maxTokensPerTick) {
        refresh(tick, maxTokensPerTick);
        return new Snapshot(tokenLimit, tokenLimit - remainingTokens, waiting.size(), granted, deferred);
    }

    private void refresh(long tick, int maxTokensPerTick) {
        int cap = Math.max(0, maxTokensPerTick);
        if (tick != currentTick) {
            currentTick = tick;
            remainingTokens = cap;
            tokenLimit = cap;
            Iterator<Map.Entry<UUID, Long>> iterator = waiting.entrySet().iterator();
            while (iterator.hasNext()) {
                long lastRequestTick = iterator.next().getValue();
                if (tick < lastRequestTick || tick - lastRequestTick > WAITING_TIMEOUT_TICKS) {
                    iterator.remove();
                }
            }
        } else if (cap != tokenLimit) {
            // If an operator changes the limit mid-tick, do not restore tokens
            // already spent earlier in this tick.
            int used = tokenLimit - remainingTokens;
            tokenLimit = cap;
            remainingTokens = Math.max(0, cap - used);
        }
    }
}
