package dev.swarmmobs.algorithm;

import dev.swarmmobs.config.SwarmConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-dimension budget registry. Only accessed by the logical server thread.
 * Weak keys ensure unloaded server levels are not retained by telemetry.
 */
public final class SwarmPathBudgetRegistry {
    private static final Map<ServerLevel, SwarmPathQueryBudget> LEVEL_BUDGETS =
            new WeakHashMap<>();

    public static boolean reserve(ServerLevel level, PathfinderMob mob, int candidateCount) {
        return forLevel(level).tryReserve(
                mob.getUUID(),
                level.getGameTime(),
                candidateCount,
                SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.get()
        );
    }

    public static SwarmPathQueryBudget.Snapshot snapshot(ServerLevel level) {
        return forLevel(level).snapshot(
                level.getGameTime(),
                SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.get()
        );
    }

    private static SwarmPathQueryBudget forLevel(ServerLevel level) {
        return LEVEL_BUDGETS.computeIfAbsent(level, unused -> new SwarmPathQueryBudget());
    }

    private SwarmPathBudgetRegistry() {}
}
