package dev.swarmmobs.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * A short-lived observation on an EXISTING dropped item. Scouts never create
 * items, grant resources, force-load chunks or replace combat AI. Worker
 * selection may favor this cue, but must still verify the item and home.
 */
public final class SwarmNestScoutSignal {
    public static final int FRESH_TICKS = 240;
    private static final String HOME = "swarmmobs:scout_home";
    private static final String OBSERVED = "swarmmobs:scout_observed";

    public static void mark(ItemEntity item, BlockPos home, long tick) {
        if (item == null || home == null || !item.isAlive()
                || item.getItem().isEmpty()) return;
        var data = item.getPersistentData();
        data.putLong(HOME, home.asLong());
        data.putLong(OBSERVED, tick);
    }

    public static boolean recentFor(ItemEntity item, BlockPos home, long tick) {
        if (item == null || home == null || !item.isAlive()) return false;
        var data = item.getPersistentData();
        return data.contains(HOME) && data.contains(OBSERVED)
                && freshFor(data.getLong(HOME), home.asLong(),
                        data.getLong(OBSERVED), tick);
    }

    /** Pure, deterministic time-and-home gate for unit tests. */
    public static boolean freshFor(long observedHome, long requestedHome,
                                   long observedTick, long currentTick) {
        return observedHome == requestedHome
                && currentTick >= observedTick
                && currentTick - observedTick <= FRESH_TICKS;
    }

    private SwarmNestScoutSignal() {}
}
