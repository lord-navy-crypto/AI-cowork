package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmNestSurveyBudget;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.colony.SwarmNestForagePolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Extremely conservative first autonomous renewable resource producer.
 * An idle Zombie harvests only AGE>=2 SWEET_BERRY_BUSH blocks around its
 * current position. No log chopping, dirt digging or block destruction.
 *
 * A harvested bush becomes AGE=1 and an actual ItemEntity is spawned at
 * that same site. The existing hauling Goal, NOT this Goal, must carry it.
 * This feature can still affect player farms, so it is OFF by default.
 */
public final class SwarmZombieBerryForageGoal extends Goal {
    private static final int[] DX = {1, -1, 0, 0, 2, -2, 0, 0, 3, -3, 0, 0,
            1, 1, -1, -1, 2, 2, -2, -2};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0, 2, -2, 0, 0, 3, -3,
            1, -1, 1, -1, 2, -2, 2, -2};
    private static final Map<ServerLevel, SwarmNestSurveyBudget> BUDGETS =
            new WeakHashMap<>();

    private final Zombie zombie;
    private BlockPos home;
    private BlockPos bush;
    private long nextSurveyTick = Long.MIN_VALUE;
    private long startTick;
    private long lastProgressTick;
    private long lastMoveTick;
    private double bestDistanceSq;
    private boolean finished;

    public SwarmZombieBerryForageGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(zombie.level() instanceof ServerLevel level) || !idle(level)) return false;
        long tick = level.getGameTime();
        if (nextSurveyTick != Long.MIN_VALUE && tick < nextSurveyTick) return false;
        nextSurveyTick = tick + SwarmConfig.NEST_BERRY_FORAGE_INTERVAL.get()
                + Math.floorMod(zombie.getId(), 31);

        var data = zombie.getPersistentData();
        if (!data.contains("SwarmColonyNest")
                || (data.contains("SwarmColonyDimension")
                && !level.dimension().location().toString()
                        .equals(data.getString("SwarmColonyDimension")))) return false;
        BlockPos candidateHome = BlockPos.of(data.getLong("SwarmColonyNest"));
        if (!level.hasChunkAt(candidateHome)
                || !level.getBlockState(candidateHome).is(SwarmNestBlocks.NEST_CORE.get())
                || !(level.getBlockEntity(candidateHome) instanceof SwarmNestBlockEntity nest)
                || zombie.distanceToSqr(candidateHome.getX() + .5,
                        candidateHome.getY() + .5, candidateHome.getZ() + .5) > 24.0 * 24.0
                || nest.nutrientPoints() + nest.legacyPoints()
                        >= SwarmNestColonyPolicy.SPAWN_COST) return false;

        // At most one bounded block survey per twelve ticks per dimension.
        if (!BUDGETS.computeIfAbsent(level, ignored -> new SwarmNestSurveyBudget())
                .trySurvey(tick, 12)) return false;

        BlockPos origin = zombie.blockPosition();
        BlockPos closest = null;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < DX.length; i++) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos site = origin.offset(DX[i], dy, DZ[i]);
                if (!level.hasChunkAt(site) || !level.hasChunkAt(site.below())
                        || playerClose(level, site)) continue;
                BlockState state = level.getBlockState(site);
                if (!state.is(Blocks.SWEET_BERRY_BUSH)) continue;
                if (!SwarmNestForagePolicy.mayHarvest(true, true, true, true, false,
                        state.getValue(SweetBerryBushBlock.AGE), nest.resources(),
                        nest.nutrientPoints() + nest.legacyPoints())) continue;
                double distance = zombie.distanceToSqr(
                        site.getX() + .5, site.getY(), site.getZ() + .5);
                if (distance < best) {
                    best = distance;
                    closest = site;
                }
            }
        }
        if (closest == null) return false;
        bush = closest;
        home = candidateHome;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (finished || !(zombie.level() instanceof ServerLevel level)
                || !idle(level) || home == null || bush == null
                || !level.hasChunkAt(home) || !level.hasChunkAt(bush)
                || !level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get())
                || !(level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest)
                || playerClose(level, bush)) return false;
        BlockState state = level.getBlockState(bush);
        return state.is(Blocks.SWEET_BERRY_BUSH)
                && SwarmNestForagePolicy.mayHarvest(true, true, true, true, false,
                        state.getValue(SweetBerryBushBlock.AGE), nest.resources(),
                        nest.nutrientPoints() + nest.legacyPoints())
                && !SwarmNestForagePolicy.expired(
                        level.getGameTime(), startTick, lastProgressTick);
    }

    @Override
    public void start() {
        finished = false;
        startTick = zombie.level().getGameTime();
        lastProgressTick = startTick;
        lastMoveTick = Long.MIN_VALUE;
        bestDistanceSq = Double.POSITIVE_INFINITY;
    }

    @Override
    public void tick() {
        if (!(zombie.level() instanceof ServerLevel level) || bush == null
                || home == null || !idle(level) || playerClose(level, bush)) {
            finished = true;
            return;
        }
        double dx = zombie.getX() - (bush.getX() + .5);
        double dy = zombie.getY() - bush.getY();
        double dz = zombie.getZ() - (bush.getZ() + .5);
        double distanceSq = dx * dx + dy * dy + dz * dz;
        long tick = level.getGameTime();
        if (distanceSq > 4.0) {
            if (distanceSq + .5 < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                lastProgressTick = tick;
            }
            if (SwarmNestForagePolicy.expired(tick, startTick, lastProgressTick)) {
                finished = true;
                return;
            }
            if (lastMoveTick == Long.MIN_VALUE
                    || tick - lastMoveTick >= SwarmNestForagePolicy.RETRY_MOVE_TICKS) {
                lastMoveTick = tick;
                zombie.getNavigation().moveTo(
                        bush.getX() + .5, bush.getY(), bush.getZ() + .5, 1.0);
            }
            return;
        }

        // Revalidate every condition just before ANY world mutation.
        if (!level.hasChunkAt(bush) || !level.hasChunkAt(home)
                || !level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get())
                || !(level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest)) {
            finished = true;
            return;
        }
        BlockState original = level.getBlockState(bush);
        if (!original.is(Blocks.SWEET_BERRY_BUSH)
                || !SwarmNestForagePolicy.mayHarvest(true, true, idle(level),
                        true, playerClose(level, bush),
                        original.getValue(SweetBerryBushBlock.AGE),
                        nest.resources(), nest.nutrientPoints() + nest.legacyPoints())) {
            finished = true;
            return;
        }
        int count = SwarmNestForagePolicy.yieldForAge(
                original.getValue(SweetBerryBushBlock.AGE));
        BlockState picked = original.setValue(SweetBerryBushBlock.AGE, 1);
        if (level.setBlockAndUpdate(bush, picked)) {
            ItemEntity realDrop = new ItemEntity(level, bush.getX() + .5,
                    bush.getY() + .35, bush.getZ() + .5,
                    new ItemStack(Items.SWEET_BERRIES, count));
            if (level.addFreshEntity(realDrop)) {
                // No direct nest deposit: real cargo must be carried later.
                nest.recordForagedBerries(count);
            } else if (level.getBlockState(bush).equals(picked)) {
                level.setBlockAndUpdate(bush, original);
            }
        }
        finished = true;
        zombie.getNavigation().stop();
    }

    @Override
    public void stop() {
        zombie.getNavigation().stop();
        home = null;
        bush = null;
        finished = true;
    }

    private boolean idle(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get() || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                || !SwarmConfig.NEST_HAULING_ENABLED.get()
                || !SwarmConfig.NEST_BERRY_FORAGING_ENABLED.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !zombie.isAlive() || zombie.isNoAi() || zombie.getTarget() != null
                || playerClose(level, zombie.blockPosition())) return false;
        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        return state.targetId() == null && !state.hasDestination();
    }

    private static boolean playerClose(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + .5, y = pos.getY() + .5, z = pos.getZ() + .5;
        int r = SwarmNestForagePolicy.PLAYER_PROTECTION_RADIUS;
        return level.players().stream().anyMatch(player -> player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(x, y, z) <= (double) r * r);
    }
}
