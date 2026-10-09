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
 * unreachable. The waiting queue protects the oldest request's required
 * tokens while allowing other agents to use spare capacity in the same tick.
 * All calls happen on the Minecraft logical server thread.
 */
public final class SwarmPathQueryBudget {
    private static final long WAITING_TIMEOUT_TICKS = 40L;

    public record Snapshot(int tokenLimit, int reservedTokens, int waiters,
                           long reservationsGranted, long reservationsDeferred) {}

    private record WaitingRequest(long lastRequestTick, int requiredTokens) {}

    private long currentTick = Long.MIN_VALUE;
    private int remainingTokens;
    private int usedTokens;
    private int tokenLimit;
    private long granted;
    private long deferred;
    private final LinkedHashMap<UUID, WaitingRequest> waiting = new LinkedHashMap<>();

    public boolean tryReserve(UUID requester, long tick, int requestedTokens, int maxTokensPerTick) {
        if (requester == null || requestedTokens < 0 || maxTokensPerTick < 0) {
            throw new IllegalArgumentException("Invalid path evidence reservation");
        }

        refresh(tick, maxTokensPerTick);
        if (requestedTokens == 0) {
            return true;
        }
        if (requestedTokens > tokenLimit) {
            // Reject reservations that cannot ever fit without filling the FIFO
            // with an impossible head request.
            deferred++;
            return false;
        }

        // Reserve the oldest waiting agent's full cost, even if that agent's
        // GoalSelector happens to tick later. Remaining tokens may still be
        // used by other mobs so a temporarily absent head cannot stall a tick.
        Map.Entry<UUID, WaitingRequest> first = waiting.isEmpty()
                ? null : waiting.entrySet().iterator().next();
        int protectedTokens = first != null && !first.getKey().equals(requester)
                ? first.getValue().requiredTokens()
                : 0;
        if (remainingTokens < requestedTokens + protectedTokens) {
            waiting.put(requester, new WaitingRequest(tick, requestedTokens));
            deferred++;
            return false;
        }

        remainingTokens -= requestedTokens;
        usedTokens += requestedTokens;
        waiting.remove(requester);
        granted++;
        return true;
    }

    public Snapshot snapshot(long tick, int maxTokensPerTick) {
        refresh(tick, maxTokensPerTick);
        return new Snapshot(tokenLimit, usedTokens, waiting.size(), granted, deferred);
    }

    private void refresh(long tick, int maxTokensPerTick) {
        int cap = Math.max(0, maxTokensPerTick);
        if (tick != currentTick) {
            currentTick = tick;
            remainingTokens = cap;
            usedTokens = 0;
            tokenLimit = cap;
            Iterator<Map.Entry<UUID, WaitingRequest>> iterator = waiting.entrySet().iterator();
            while (iterator.hasNext()) {
                long lastRequestTick = iterator.next().getValue().lastRequestTick();
                if (tick < lastRequestTick || tick - lastRequestTick > WAITING_TIMEOUT_TICKS) {
                    iterator.remove();
                }
            }
        } else if (cap != tokenLimit) {
            // Mid-tick config changes must not give back spent tokens.
            tokenLimit = cap;
            remainingTokens = Math.max(0, cap - usedTokens);
        }
    }
}
