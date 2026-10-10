package dev.swarmmobs.colony;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Bounded, loaded-Nest-Core-local record of real resources observed by scouts.
 * A report is an observation, NOT authority to create any block/animal/item.
 * Workers must check the live world again before any harvesting action.
 *
 * Inspired by indirect recruitment and competing short-lived local signals;
 * the parameters below are GAME values, not measured insect rates.
 */
public final class SwarmNestOpportunityBoard {
    public static final int MAX_LEADS = 24;
    public static final int MAX_RADIUS = 28;
    public static final int FRESH_TICKS = 360;
    public static final int LEASE_TICKS = 100;
    public static final int MAX_ACTIVE_WORKERS = 6;

    public enum Type { ANIMAL, BLOCK }

    public record Position(int x, int y, int z) {
        public long squaredDistance(Position other) {
            long dx = (long) x - other.x;
            long dy = (long) y - other.y;
            long dz = (long) z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Opportunity(Type type, Position position, UUID animalId,
                              SwarmNestColonyPolicy.Kind kind,
                              long lastSeen, int sightings) {}

    private record Key(Type type, Position position, UUID animalId) {}
    private static final class Entry {
        private Opportunity report;
        private UUID owner;
        private long until;
        Entry(Opportunity report) { this.report = report; }
    }

    private final Map<Key, Entry> leads = new LinkedHashMap<>();
    private long reported, claimed, invalidated;

    public boolean publishAnimal(UUID animal, Position position, long now, Position home) {
        if (animal == null) return false;
        return publish(new Key(Type.ANIMAL, null, animal),
                Type.ANIMAL, position, animal,
                SwarmNestColonyPolicy.Kind.NUTRIENT, now, home);
    }

    public boolean publishBlock(Position position, SwarmNestColonyPolicy.Kind kind,
                                long now, Position home) {
        if (kind == null || kind == SwarmNestColonyPolicy.Kind.NONE) return false;
        return publish(new Key(Type.BLOCK, position, null), Type.BLOCK, position,
                null, kind, now, home);
    }

    private boolean publish(Key key, Type type, Position position, UUID animal,
                            SwarmNestColonyPolicy.Kind kind, long now, Position home) {
        if (position == null || home == null || now < 0
                || home.squaredDistance(position) > (long) MAX_RADIUS * MAX_RADIUS) {
            return false;
        }
        prune(now);
        Entry previous = leads.get(key);
        if (previous != null) {
            if (now < previous.report.lastSeen()) return false;
            previous.report = new Opportunity(type, position, animal, kind, now,
                    Math.min(16, previous.report.sightings() + 1));
            reported++;
            return true;
        }
        if (leads.size() >= MAX_LEADS) {
            Key evict = null;
            for (var entry : leads.entrySet()) {
                if (entry.getValue().owner == null
                        || entry.getValue().until < now) {
                    evict = entry.getKey();
                    break;
                }
            }
            if (evict == null) return false;
            leads.remove(evict);
        }
        leads.put(key, new Entry(new Opportunity(type, position, animal, kind, now, 1)));
        reported++;
        return true;
    }

    /** Exclusive nearby worker leasing; no forced chunk loads or entity handles. */
    public Opportunity reserve(UUID worker, Position workerPos, Type type, long now,
                               int maxDistance, Predicate<Opportunity> eligible,
                               ToDoubleFunction<Opportunity> score) {
        if (worker == null || workerPos == null || type == null
                || eligible == null || score == null || now < 0) return null;
        prune(now);
        for (Entry entry : leads.values()) {
            if (worker.equals(entry.owner) && entry.until >= now) {
                if (entry.report.type() == type
                        && workerPos.squaredDistance(entry.report.position())
                                <= (long) maxDistance * maxDistance
                        && eligible.test(entry.report)) return entry.report;
                entry.owner = null;
                entry.until = 0;
            }
        }
        if (activeWorkers(now) >= MAX_ACTIVE_WORKERS) return null;
        Entry best = null;
        double lowest = Double.POSITIVE_INFINITY;
        for (Entry entry : leads.values()) {
            if (entry.report.type() != type || (entry.owner != null && entry.until >= now)
                    || workerPos.squaredDistance(entry.report.position())
                            > (long) maxDistance * maxDistance
                    || !eligible.test(entry.report)) continue;
            double candidate = score.applyAsDouble(entry.report);
            if (Double.isFinite(candidate) && candidate < lowest) {
                lowest = candidate;
                best = entry;
            }
        }
        if (best == null) return null;
        best.owner = worker;
        best.until = now + LEASE_TICKS;
        claimed++;
        return best.report;
    }

    public boolean renew(Opportunity report, UUID worker, long now) {
        Entry entry = leads.get(key(report));
        if (entry == null || worker == null || !worker.equals(entry.owner)
                || entry.until < now || now - entry.report.lastSeen() > FRESH_TICKS) {
            return false;
        }
        entry.until = now + LEASE_TICKS;
        return true;
    }

    public void release(Opportunity report, UUID worker) {
        Entry entry = leads.get(key(report));
        if (entry != null && worker != null && worker.equals(entry.owner)) {
            entry.owner = null;
            entry.until = 0;
        }
    }

    public void invalidate(Opportunity report) {
        if (report != null && leads.remove(key(report)) != null) invalidated++;
    }

    public int size(long now) { prune(now); return leads.size(); }
    public int activeWorkers(long now) {
        prune(now);
        int result = 0;
        for (Entry entry : leads.values()) {
            if (entry.owner != null && entry.until >= now) result++;
        }
        return result;
    }
    public long reports() { return reported; }
    public long claimedJobs() { return claimed; }
    public long invalidReports() { return invalidated; }

    private static Key key(Opportunity report) {
        if (report == null) return null;
        return report.type() == Type.ANIMAL
                ? new Key(Type.ANIMAL, null, report.animalId())
                : new Key(Type.BLOCK, report.position(), null);
    }

    private void prune(long now) {
        leads.values().removeIf(entry -> now < entry.report.lastSeen()
                || now - entry.report.lastSeen() > FRESH_TICKS);
    }
}
