package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmNestSurveyBudget;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.colony.SwarmNestHaulLease;
import dev.swarmmobs.colony.SwarmNestHaulPolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Opt-in real-item worker logistics. Zombies do NOT craft, invent resources,
 * replace equipment or own a hidden virtual inventory. They escort the same
 * dropped ItemEntity from a reachable source to their loaded home Nest Core.
 * If interrupted, the physical item is left where the worker last held it.
 *
 * Search and navigation command creation are both throttled per dimension.
 * Vanilla close-range combat and high-priority Zombie engineering take over.
 */
public final class SwarmZombieColonyHaulGoal extends Goal {
    private enum Phase { NONE, TO_ITEM, TO_NEST }

    private static final Map<ServerLevel, SwarmNestSurveyBudget> SEARCH_BUDGETS =
            new WeakHashMap<>();
    private static final Map<ServerLevel, SwarmNestSurveyBudget> MOVE_BUDGETS =
            new WeakHashMap<>();

    private final Zombie zombie;
    private ItemEntity item;
    private BlockPos home;
    private Phase phase = Phase.NONE;
    private long nextSearchTick = Long.MIN_VALUE;
    private long lastMoveTick = Long.MIN_VALUE;
    private long startTick;
    private long lastProgressTick;
    private double bestRemainingDistanceSq = Double.POSITIVE_INFINITY;

    public SwarmZombieColonyHaulGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(zombie.level() instanceof ServerLevel level) || !idle(level)) {
            return false;
        }
        long tick = level.getGameTime();
        if (nextSearchTick != Long.MIN_VALUE && tick < nextSearchTick) {
            return false;
        }
        nextSearchTick = tick + SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.get()
                + Math.floorMod(zombie.getId(), 23);

        var persistent = zombie.getPersistentData();
        if (!persistent.contains("SwarmColonyNest")
                || (persistent.contains("SwarmColonyDimension")
                        && !level.dimension().location().toString()
                                .equals(persistent.getString("SwarmColonyDimension")))) {
            return false;
        }
        BlockPos targetHome = BlockPos.of(persistent.getLong("SwarmColonyNest"));
        if (!level.hasChunkAt(targetHome)
                || !(level.getBlockEntity(targetHome) instanceof SwarmNestBlockEntity nest)
                || !level.getBlockState(targetHome).is(SwarmNestBlocks.NEST_CORE.get())
                || nest.resources() >= SwarmNestColonyPolicy.MAX_STORED_RESOURCES
                || zombie.distanceToSqr(targetHome.getX() + 0.5,
                        targetHome.getY() + 0.5, targetHome.getZ() + 0.5) > 24.0 * 24.0) {
            return false;
        }
        // One expensive item query per four ticks in the dimension,
        // regardless of how many zombies become idle at once.
        if (!SEARCH_BUDGETS.computeIfAbsent(level, ignored -> new SwarmNestSurveyBudget())
                .trySurvey(tick, 4)) {
            return false;
        }
        int radius = SwarmConfig.NEST_HAUL_SEARCH_RADIUS.get();
        String workerId = zombie.getUUID().toString();
        ItemEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (ItemEntity drop : level.getEntitiesOfClass(
                ItemEntity.class, zombie.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && !candidate.getItem().isEmpty())) {
            var kind = SwarmNestBlockEntity.classify(drop.getItem());
            if (!SwarmNestHaulPolicy.eligible(true, true, true,
                    kind != SwarmNestColonyPolicy.Kind.NONE,
                    playerNear(level, drop.getX(), drop.getY(), drop.getZ(), 6.0),
                    drop.getItem().getCount(), SwarmConfig.NEST_HAUL_MAX_STACK.get(),
                    SwarmNestColonyPolicy.MAX_STORED_RESOURCES - nest.resources())) {
                continue;
            }
            if (!SwarmNestHaulPolicy.hasRoomFor(
                    nest.resources(), drop.getItem().getCount(), kind)
                    || SwarmNestHaulLease.claimedByAnother(drop, workerId, tick)
                    || drop.distanceToSqr(targetHome.getX() + .5,
                            targetHome.getY() + .5, targetHome.getZ() + .5) < 16.0) {
                // Passive intake already owns drops very close to the core.
                continue;
            }
            // Nearby items still win unless the colony actually lacks a
            // category for construction or reproduction. Pure score, no
            // additional pathfinding queries and no spider-wide scans.
            double score = SwarmNestHaulPolicy.pickupScore(kind,
                    zombie.distanceToSqr(drop), nest.soilPoints(),
                    nest.timberPoints(), nest.nutrientPoints(), nest.legacyPoints(),
                    nest.chamberLevel(), SwarmConfig.NEST_MAX_POPULATION.get());
            if (score < bestScore) {
                bestScore = score;
                best = drop;
            }
        }
        if (best == null || !SwarmNestHaulLease.tryClaim(best, workerId, tick)) return false;
        item = best;
        home = targetHome;
        phase = Phase.TO_ITEM;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(zombie.level() instanceof ServerLevel level)
                || !idle(level) || phase == Phase.NONE || home == null || item == null
                || !level.hasChunkAt(home)
                || !(level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest)
                || !level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get())
                || !SwarmNestHaulPolicy.hasRoomFor(nest.resources(),
                        item.getItem().getCount(),
                        SwarmNestBlockEntity.classify(item.getItem()))) {
            return false;
        }
        return SwarmNestHaulPolicy.canContinue(
                level.getGameTime() - startTick, true,
                item.isAlive() && !item.getItem().isEmpty(), true);
    }

    @Override
    public void start() {
        startTick = zombie.level().getGameTime();
        lastMoveTick = Long.MIN_VALUE;
        lastProgressTick = startTick;
        bestRemainingDistanceSq = Double.POSITIVE_INFINITY;
    }

    @Override
    public void tick() {
        if (!(zombie.level() instanceof ServerLevel level)
                || item == null || home == null || !item.isAlive()) {
            phase = Phase.NONE;
            return;
        }
        long tick = level.getGameTime();
        String workerId = zombie.getUUID().toString();
        if (tick % 20 == 0 && !SwarmNestHaulLease.tryClaim(item, workerId, tick)) {
            phase = Phase.NONE;
            return;
        }
        if (phase == Phase.TO_ITEM) {
            if (zombie.distanceToSqr(item) <= 2.25) {
                phase = Phase.TO_NEST;
                // Track progress independently on the return route.
                lastProgressTick = tick;
                bestRemainingDistanceSq = Double.POSITIVE_INFINITY;
                zombie.getNavigation().stop();
            } else {
                follow(level, item.getX(), item.getY(), item.getZ(), tick);
                return;
            }
        }
        if (phase == Phase.TO_NEST) {
            // Reject abnormal external relocation: the same item MUST still
            // be physically near the worker. It may not be deposited from
            // an unrelated location simply because the worker reached home.
            if (item.level() != level || zombie.distanceToSqr(item) > 16.0) {
                phase = Phase.NONE;
                return;
            }
            boolean atDock = zombie.distanceToSqr(
                    home.getX() + .5, home.getY() + .5, home.getZ() + .5) <= 5.0;
            // Drag the original ItemEntity, normally at most every three
            // ticks; always synchronize it at the dock before depositing.
            if (tick % 3 == 0 || atDock) {
                item.setDeltaMovement(Vec3.ZERO);
                item.setPos(zombie.getX(), zombie.getY() + .65, zombie.getZ());
            }
            if (atDock) {
                double cargoToDock = item.distanceToSqr(
                        home.getX() + .5, home.getY() + .5, home.getZ() + .5);
                if (cargoToDock <= 9.0
                        && level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest) {
                    nest.acceptHaulDelivery(item, SwarmConfig.NEST_HAUL_MAX_STACK.get());
                }
                phase = Phase.NONE;
                zombie.getNavigation().stop();
                return;
            }
            follow(level, home.getX() + .5, home.getY(), home.getZ() + .5, tick);
        }
    }

    private void follow(ServerLevel level, double x, double y, double z, long tick) {
        double remaining = zombie.distanceToSqr(x, y, z);
        if (SwarmNestHaulPolicy.progress(bestRemainingDistanceSq, remaining)) {
            bestRemainingDistanceSq = remaining;
            lastProgressTick = tick;
        }
        if (SwarmNestHaulPolicy.stalled(tick, lastProgressTick)) {
            // Unreachable paths must not monopolize a lease until full task
            // expiry. Goal.stop releases the unconsumed physical item.
            phase = Phase.NONE;
            zombie.getNavigation().stop();
            return;
        }
        navigate(level, x, y, z, tick);
    }

    private void navigate(ServerLevel level, double x, double y, double z, long tick) {
        if (lastMoveTick != Long.MIN_VALUE
                && tick - lastMoveTick < SwarmNestHaulPolicy.RETRY_NAV_TICKS) {
            return;
        }
        if (!MOVE_BUDGETS.computeIfAbsent(level, ignored -> new SwarmNestSurveyBudget())
                .trySurvey(tick, 2)) return;
        lastMoveTick = tick;
        zombie.getNavigation().moveTo(x, y, z, 1.0);
    }

    @Override
    public void stop() {
        if (item != null) {
            // On interruption, leave a real item in the world, not a phantom
            // carried stack; an expired claim allows another worker to retry.
            SwarmNestHaulLease.release(item, zombie.getUUID().toString());
        }
        if (phase != Phase.NONE) zombie.getNavigation().stop();
        item = null;
        home = null;
        phase = Phase.NONE;
    }

    private boolean idle(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get() || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                || !SwarmConfig.NEST_HAULING_ENABLED.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !zombie.isAlive() || zombie.isNoAi() || zombie.getTarget() != null) {
            return false;
        }
        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        return state.targetId() == null && !state.hasDestination();
    }

    private static boolean playerNear(ServerLevel level, double x, double y,
                                      double z, double radius) {
        return level.players().stream().anyMatch(player ->
                !player.isSpectator()
                        && player.distanceToSqr(x, y, z) <= radius * radius);
    }
}
