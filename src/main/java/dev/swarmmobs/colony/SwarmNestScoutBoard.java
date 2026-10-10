package dev.swarmmobs.colony;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Pure Java, transient stigmergic job board owned by a loaded Nest Core.
 * Stores only a handful of sightings of physical dropped ItemEntities,
 * never objects, inventory stacks, or Minecraft world/chunk references.
 */
public final class SwarmNestScoutBoard {
    public static final int MAX_LEADS = 8;
    public static final int MAX_DISTANCE = 28;
    public static final int FRESH_TICKS = 240;
    private static final int CLAIM_TICKS = 180;
    private final Map<UUID, Entry> leads = new LinkedHashMap<>();

    public record Position(int x, int y, int z) {
        public long distanceSquared(Position other) {
            long dx = (long) x - other.x;
            long dy = (long) y - other.y;
            long dz = (long) z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Lead(UUID itemId, Position position,
                       SwarmNestColonyPolicy.Kind kind, long observedTick) {}

    private static final class Entry {
        private Lead lead;
        private UUID worker;
        private long claimedUntil;
        private Entry(Lead lead) { this.lead = lead; }
    }

    public boolean publish(UUID itemId, Position position,
                           SwarmNestColonyPolicy.Kind kind, long tick,
                           Position home) {
        if (itemId == null || position == null || home == null || tick < 0
                || kind == null || kind == SwarmNestColonyPolicy.Kind.NONE
                || !within(home, position, MAX_DISTANCE)) return false;
        prune(tick);
        Entry previous = leads.get(itemId);
        if (previous != null) {
            if (tick < previous.lead.observedTick()) return false;
            previous.lead = new Lead(itemId, position, kind, tick);
            return true;
        }
        if (leads.size() >= MAX_LEADS) {
            UUID oldest = null;
            for (var e : leads.entrySet()) {
                if (e.getValue().worker == null
                        || e.getValue().claimedUntil < tick) {
                    oldest = e.getKey();
                    break;
                }
            }
            if (oldest == null) return false;
            leads.remove(oldest);
        }
        leads.put(itemId, new Entry(new Lead(itemId, position, kind, tick)));
        return true;
    }

    public Lead reserve(UUID worker, Position workerPos, long tick, int radius) {
        return reserve(worker, workerPos, tick, radius,
                lead -> true, lead -> workerPos.distanceSquared(lead.position()));
    }

    /**
     * Filter reports against current stock capacity, then score them by
     * distance and shortages. Pure Java callbacks cannot query the world.
     */
    public Lead reserve(UUID worker, Position workerPos, long tick, int radius,
                        Predicate<Lead> acceptable, ToDoubleFunction<Lead> score) {
        if (worker == null || workerPos == null || acceptable == null || score == null)
            return null;
        prune(tick);
        // A worker cannot hold more than one report at a time.
        for (Entry entry : leads.values()) {
            if (worker.equals(entry.worker) && entry.claimedUntil >= tick) {
                if (acceptable.test(entry.lead)
                        && within(workerPos, entry.lead.position(), Math.max(1, radius))) {
                    return entry.lead;
                }
                entry.worker = null;
                entry.claimedUntil = 0L;
            }
        }
        Entry best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Entry entry : leads.values()) {
            if (entry.worker != null && entry.claimedUntil >= tick) continue;
            if (!within(workerPos, entry.lead.position(), Math.max(1, radius))
                    || !acceptable.test(entry.lead)) continue;
            double candidate = score.applyAsDouble(entry.lead);
            if (Double.isFinite(candidate) && candidate < bestScore) {
                best = entry;
                bestScore = candidate;
            }
        }
        if (best == null) return null;
        best.worker = worker;
        best.claimedUntil = tick + CLAIM_TICKS;
        return best.lead;
    }

    /** Only the owner of an unexpired lease can refresh it. */
    public boolean renew(UUID itemId, UUID worker, long tick) {
        Entry entry = leads.get(itemId);
        if (entry == null || worker == null || !worker.equals(entry.worker)
                || tick > entry.claimedUntil
                || !fresh(entry.lead.observedTick(), tick)) return false;
        entry.claimedUntil = tick + CLAIM_TICKS;
        return true;
    }

    public void release(UUID itemId, UUID worker) {
        Entry entry = leads.get(itemId);
        if (entry != null && worker != null && worker.equals(entry.worker)) {
            entry.worker = null;
            entry.claimedUntil = 0L;
        }
    }

    public void discard(UUID itemId) { leads.remove(itemId); }

    public int size(long tick) { prune(tick); return leads.size(); }

    private void prune(long tick) {
        leads.values().removeIf(entry -> !fresh(entry.lead.observedTick(), tick));
    }

    private static boolean fresh(long observed, long now) {
        return now >= observed && now - observed <= FRESH_TICKS;
    }

    private static boolean within(Position a, Position b, int radius) {
        return a.distanceSquared(b) <= (long) radius * radius;
    }

    public SwarmNestScoutBoard() {}
}
