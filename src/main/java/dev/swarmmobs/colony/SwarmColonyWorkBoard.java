package dev.swarmmobs.colony;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One loaded nest's bounded, transient work-site leases. No chunk, entity,
 * inventory or BlockPos reference is retained. Shared by independent workers
 * so an ant-style colony does not assign three miners to the same block.
 *
 * This board does not confer authority to modify blocks. Every worker still
 * checks the live world and current operator switches at execution time.
 */
public final class SwarmColonyWorkBoard {
    public static final int MAX_SITES = 24;
    public static final int LEASE_TICKS = 60;
    private final Map<Long, Lease> leases = new HashMap<>();

    private record Lease(UUID worker, long until) {}

    public boolean claim(long site, UUID worker, long now) {
        if (worker == null || now < 0) return false;
        expire(now);
        Lease existing = leases.get(site);
        if (existing != null && !existing.worker().equals(worker)) return false;
        if (existing == null && leases.size() >= MAX_SITES) return false;
        leases.put(site, new Lease(worker, now + LEASE_TICKS));
        return true;
    }

    public boolean claimedByAnother(long site, UUID worker, long now) {
        Lease entry = leases.get(site);
        return entry != null && entry.until() >= now
                && !entry.worker().equals(worker);
    }

    public boolean owned(long site, UUID worker, long now) {
        Lease entry = leases.get(site);
        return entry != null && entry.until() >= now
                && entry.worker().equals(worker);
    }

    public void release(long site, UUID worker) {
        Lease entry = leases.get(site);
        if (entry != null && entry.worker().equals(worker)) leases.remove(site);
    }

    public int size(long now) {
        expire(now);
        return leases.size();
    }

    private void expire(long now) {
        leases.values().removeIf(entry -> entry.until() < now);
    }
}
