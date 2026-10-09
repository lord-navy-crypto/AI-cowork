package dev.swarmmobs.colony;

import net.minecraft.world.entity.item.ItemEntity;

/**
 * A temporary worker claim on an existing ItemEntity. No virtual inventory
 * or new item is made, and the claim expires after crashes/unloads.
 * Minecraft's own item entity remains the single source of truth for matter.
 */
public final class SwarmNestHaulLease {
    private static final String OWNER = "swarmmobs:haul_worker";
    private static final String EXPIRES = "swarmmobs:haul_expires";

    public static boolean tryClaim(ItemEntity item, String worker, long now) {
        if (item == null || !item.isAlive() || item.getItem().isEmpty()) {
            return false;
        }
        var tag = item.getPersistentData();
        if (!SwarmNestHaulPolicy.mayClaim(tag.getString(OWNER), tag.getLong(EXPIRES),
                worker, now)) {
            return false;
        }
        tag.putString(OWNER, worker);
        tag.putLong(EXPIRES, now + SwarmNestHaulPolicy.LEASE_TICKS);
        return true;
    }

    public static boolean claimedByAnother(ItemEntity item, String worker, long now) {
        var tag = item.getPersistentData();
        return !SwarmNestHaulPolicy.mayClaim(tag.getString(OWNER),
                tag.getLong(EXPIRES), worker, now);
    }

    public static boolean isClaimed(ItemEntity item, long now) {
        var tag = item.getPersistentData();
        return !tag.getString(OWNER).isEmpty() && tag.getLong(EXPIRES) >= now;
    }

    public static void release(ItemEntity item, String worker) {
        if (item == null) return;
        var tag = item.getPersistentData();
        if (worker.equals(tag.getString(OWNER))) {
            tag.remove(OWNER);
            tag.remove(EXPIRES);
        }
    }

    private SwarmNestHaulLease() {}
}
