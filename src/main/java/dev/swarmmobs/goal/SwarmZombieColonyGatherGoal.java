package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmNestSurveyBudget;
import dev.swarmmobs.colony.SwarmColonyGatherPolicy;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Real-world resource producer, subordinate to combat and cargo transport.
 * Soil and raw logs can be removed even from player builds when the operator
 * enables this gameplay mechanic. Mature crops use vanilla block drops.
 * No fabricated item entities and no direct Nest Core inventory credits.
 */
public final class SwarmZombieColonyGatherGoal extends Goal {
    private static final int RADIUS = 4;
    private static final Map<ServerLevel, SwarmNestSurveyBudget> BUDGETS =
            new WeakHashMap<>();
    private final Zombie zombie;
    private BlockPos home;
    private BlockPos site;
    private BlockState original;
    private SwarmNestColonyPolicy.Kind selectedKind;
    private long nextSurvey = Long.MIN_VALUE;
    private long started;
    private long lastProgress;
    private long lastNavigate = Long.MIN_VALUE;
    private double bestDistance;
    private boolean done;

    public SwarmZombieColonyGatherGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(zombie.level() instanceof ServerLevel level) || !idle(level)) return false;
        long tick = level.getGameTime();
        if (nextSurvey != Long.MIN_VALUE && tick < nextSurvey) return false;
        nextSurvey = tick + SwarmConfig.NEST_GATHER_INTERVAL.get()
                + Math.floorMod(zombie.getId(), 37);
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
                        candidateHome.getY() + .5, candidateHome.getZ() + .5)
                        > 24.0 * 24.0) return false;
        if (!BUDGETS.computeIfAbsent(level, unused -> new SwarmNestSurveyBudget())
                .trySurvey(tick, 12)) return false;
        double best = Double.POSITIVE_INFINITY;
        BlockPos chosen = null;
        BlockState stateChosen = null;
        SwarmNestColonyPolicy.Kind kindChosen = null;
        BlockPos center = zombie.blockPosition();
        // Fixed 9x9x4 maximum local inspection; no scans of unloaded chunks.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos test = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(test)) continue;
                    BlockState state = level.getBlockState(test);
                    var kind = category(level, test, state);
                    if (!SwarmColonyGatherPolicy.needs(kind,
                            nest.soilPoints(), nest.timberPoints(),
                            nest.nutrientPoints() + nest.legacyPoints(), nest.resources())
                            || !validBlock(level, test, state)) continue;
                    double dx2 = zombie.getX() - (test.getX() + .5);
                    double dz2 = zombie.getZ() - (test.getZ() + .5);
                    double dy2 = zombie.getY() - test.getY();
                    double score = SwarmColonyGatherPolicy.score(kind,
                            dx2 * dx2 + dy2 * dy2 + dz2 * dz2,
                            nest.soilPoints(), nest.timberPoints(),
                            nest.nutrientPoints() + nest.legacyPoints());
                    if (score < best) {
                        best = score;
                        chosen = test;
                        stateChosen = state;
                        kindChosen = kind;
                    }
                }
            }
        }
        if (chosen == null) return false;
        home = candidateHome;
        site = chosen;
        original = stateChosen;
        selectedKind = kindChosen;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (done || !(zombie.level() instanceof ServerLevel level)
                || !idle(level) || home == null || site == null
                || !level.hasChunkAt(home) || !level.hasChunkAt(site)
                || !(level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest)
                || !level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get())
                || !level.getBlockState(site).equals(original)) return false;
        return validBlock(level, site, original)
                && SwarmColonyGatherPolicy.needs(selectedKind,
                        nest.soilPoints(), nest.timberPoints(),
                        nest.nutrientPoints() + nest.legacyPoints(), nest.resources())
                && !SwarmColonyGatherPolicy.expired(level.getGameTime(), started, lastProgress);
    }

    @Override
    public void start() {
        done = false;
        started = zombie.level().getGameTime();
        lastProgress = started;
        lastNavigate = Long.MIN_VALUE;
        bestDistance = Double.POSITIVE_INFINITY;
    }

    @Override
    public void tick() {
        if (!(zombie.level() instanceof ServerLevel level) || site == null
                || !canContinueToUse()) {
            done = true;
            return;
        }
        double dx = zombie.getX() - (site.getX() + .5);
        double dy = zombie.getY() - site.getY();
        double dz = zombie.getZ() - (site.getZ() + .5);
        double d2 = dx * dx + dy * dy + dz * dz;
        long now = level.getGameTime();
        if (d2 > 5.0) {
            if (d2 + .5 < bestDistance) {
                bestDistance = d2;
                lastProgress = now;
            }
            if (lastNavigate == Long.MIN_VALUE
                    || now - lastNavigate >= SwarmColonyGatherPolicy.RETRY_NAV_TICKS) {
                lastNavigate = now;
                zombie.getNavigation().moveTo(
                        site.getX() + .5, site.getY(), site.getZ() + .5, 1.0);
            }
            return;
        }
        // Vanilla world loot is the sole source of materials; breaking one
        // block is intentional, including player-laid wood/soil when enabled.
        level.destroyBlock(site, true, zombie);
        done = true;
        zombie.getNavigation().stop();
    }

    @Override
    public void stop() {
        zombie.getNavigation().stop();
        site = null;
        original = null;
        home = null;
        selectedKind = null;
        done = true;
    }

    /** Return only physically useful raw materials or ripe plant blocks. */
    public static SwarmNestColonyPolicy.Kind category(
            ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.LOGS)) return SwarmNestColonyPolicy.Kind.TIMBER;
        if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.MUD)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL))
            return SwarmNestColonyPolicy.Kind.SOIL;
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))
            return SwarmNestColonyPolicy.Kind.NUTRIENT;
        if (state.is(Blocks.NETHER_WART)
                && state.getValue(NetherWartBlock.AGE) == 3)
            return SwarmNestColonyPolicy.Kind.NUTRIENT;
        if (state.is(Blocks.COCOA)
                && state.getValue(CocoaBlock.AGE) == 2)
            return SwarmNestColonyPolicy.Kind.NUTRIENT;
        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM))
            return SwarmNestColonyPolicy.Kind.NUTRIENT;
        if (state.is(Blocks.SUGAR_CANE)
                && level.hasChunkAt(pos.below())
                && level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE))
            return SwarmNestColonyPolicy.Kind.NUTRIENT;
        return SwarmNestColonyPolicy.Kind.NONE;
    }

    private static boolean validBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && !state.hasBlockEntity()
                && state.getDestroySpeed(level, pos) >= 0.0f
                && state.getDestroySpeed(level, pos) <= 4.0f
                && level.getFluidState(pos).isEmpty();
    }

    private boolean idle(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get() || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                || !SwarmConfig.NEST_HAULING_ENABLED.get()
                || !SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !zombie.isAlive() || zombie.isNoAi()
                || zombie.getTarget() != null) return false;
        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        return state.targetId() == null && !state.hasDestination();
    }
}
