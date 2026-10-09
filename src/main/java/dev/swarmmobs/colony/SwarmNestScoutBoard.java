package dev.swarmmobs.colony;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transient, bounded stigmergic job board owned by ONE loaded Nest Core.
 * Observation is only a hint about a physical ItemEntity, never an item
 * inventory. Reserving a lead does not reserve the physical cargo: a worker
 * must verify and claim the original ItemEntity when it arrives.
 *
 * The board intentionally does not persist or ticket chunks: all leads
 * disappear on server reload and expire during regular operations.
 */
public final class SwarmNestScoutBoard {
    public static final int MAX_LEADS = 8;
    public static final int MAX_DISTANCE = 28;
    private static final int CLAIM_TICKS = 180;
    private final Map<UUID, Entry> leads = new LinkedHashMap<>();

    public record Lead(UUID itemId, BlockPos position,
                       SwarmNestColonyPolicy.Kind kind, long observedTick) {}

    private static final class Entry {
        private Lead lead;
        private UUID worker;
        private long claimedUntil;
        private Entry(Lead lead) { this.lead = lead; }
    }

    public boolean publish(UUID itemId, BlockPos position,
                           SwarmNestColonyPolicy.Kind kind, long tick,
                           BlockPos home) {
        if (itemId == null || position == null || home == null || tick < 0
                || kind == null || kind == SwarmNestColonyPolicy.Kind.NONE
                || !within(home, position, MAX_DISTANCE)) return false;
        prune(tick);
        Entry previous = leads.get(itemId);
        if (previous != null) {
            if (tick < previous.lead.observedTick()) return false;
            previous.lead = new Lead(itemId, position.immutable(), kind, tick);
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
            if (oldest == null) return false; // never evict an active reservation
            leads.remove(oldest);
        }
        leads.put(itemId, new Entry(new Lead(itemId, position.immutable(), kind, tick)));
        return true;
    }

    public Lead reserve(UUID worker, BlockPos workerPos, long tick,
                        int radius) {
        if (worker == null || workerPos == null) return null;
        prune(tick);
        Entry best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Entry entry : leads.values()) {
            if (entry.worker != null && !entry.worker.equals(worker)
                    && entry.claimedUntil >= tick) continue;
            if (!within(workerPos, entry.lead.position(), Math.max(1, radius))) continue;
            double distance = workerPos.distSqr(entry.lead.position());
            if (distance < bestDistance) {
                best = entry;
                bestDistance = distance;
            }
        }
        if (best == null) return null;
        best.worker = worker;
        best.claimedUntil = tick + CLAIM_TICKS;
        return best.lead;
    }

    /** Touch only an existing matching reservation. */
    public boolean renew(UUID itemId, UUID worker, long tick) {
        Entry entry = leads.get(itemId);
        if (entry == null || worker == null || !worker.equals(entry.worker)
                || tick > entry.claimedUntil
                || !SwarmNestScoutSignal.freshFor(0L, 0L,
                        entry.lead.observedTick(), tick)) return false;
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
        leads.values().removeIf(entry -> !SwarmNestScoutSignal.freshFor(
                0L, 0L, entry.lead.observedTick(), tick));
    }

    private static boolean within(BlockPos a, BlockPos b, int radius) {
        return a.distSqr(b) <= (double) radius * radius;
    }
}
