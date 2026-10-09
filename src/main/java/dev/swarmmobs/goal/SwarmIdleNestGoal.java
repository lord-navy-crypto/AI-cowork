package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmNestSitePolicy;
import dev.swarmmobs.algorithm.SwarmNestSurveyBudget;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Opt-in v0.13 ant-like nest founding: idle Zombies in a small local colony
 * occasionally deposit one persistent, inert Nest Core on natural ground.
 *
 * This goal takes no MOVE/LOOK flags, never performs path queries, never force
 * loads chunks, never breaks or replaces a non-air block, and does not tick a
 * block entity. Player targets immediately disable founding.
 *
 * The core is a future stigmergic environmental marker; it does NOT yet spawn
 * mobs, store items, distribute commands or change player combat.
 */
public final class SwarmIdleNestGoal extends Goal {
    private static final int SURVEY_GAP_TICKS = 40;
    private static final int PEER_RADIUS = 6;
    private static final int EXISTING_NEST_RADIUS = 8;
    private static final int[] OFFSETS_X = {2, -2, 0, 0, 2, -2, 2, -2};
    private static final int[] OFFSETS_Z = {0, 0, 2, -2, 2, 2, -2, -2};
    private static final Map<ServerLevel, SwarmNestSurveyBudget> BUDGETS = new WeakHashMap<>();

    private final Zombie zombie;
    private BlockPos proposed;
    private long nextAttemptTick = Long.MIN_VALUE;

    public SwarmIdleNestGoal(Zombie zombie) {
        this.zombie = zombie;
    }

    @Override
    public boolean canUse() {
        if (!(zombie.level() instanceof ServerLevel level)
                || !baseEligible(level)) {
            return false;
        }
        long tick = level.getGameTime();
        if (nextAttemptTick != Long.MIN_VALUE && tick < nextAttemptTick) {
            return false;
        }
        nextAttemptTick = tick + SwarmConfig.NEST_BUILD_INTERVAL_TICKS.get()
                + Math.floorMod(zombie.getId(), 37);

        SwarmNestSurveyBudget budget =
                BUDGETS.computeIfAbsent(level, unused -> new SwarmNestSurveyBudget());
        if (!budget.trySurvey(tick, SURVEY_GAP_TICKS)) {
            return false;
        }

        int localPopulation = 1 + level.getEntitiesOfClass(
                PathfinderMob.class,
                zombie.getBoundingBox().inflate(PEER_RADIUS),
                peer -> peer != zombie && peer.isAlive()
                        && (peer.getType() == net.minecraft.world.entity.EntityType.ZOMBIE
                        || peer.getType() == net.minecraft.world.entity.EntityType.SKELETON
                        || peer.getType() == net.minecraft.world.entity.EntityType.SPIDER
                        || peer.getType() == net.minecraft.world.entity.EntityType.CREEPER)
        ).size();

        if (localPopulation < SwarmConfig.NEST_MIN_GROUP_SIZE.get()) {
            return false;
        }

        // Check local block terrain only after both the personal and global
        // quotas have passed. This prevents O(mobs x nest-radius^2) scanning.
        if (nearbyNest(level, zombie.blockPosition())) {
            return false;
        }
        proposed = findBuildSite(level);
        return SwarmNestSitePolicy.eligible(
                true, true, false, false, false,
                localPopulation, SwarmConfig.NEST_MIN_GROUP_SIZE.get(),
                proposed != null, false
        );
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (!(zombie.level() instanceof ServerLevel level) || !baseEligible(level)
                || proposed == null || nearbyNest(level, proposed)
                || !isNaturalSite(level, proposed)) {
            proposed = null;
            return;
        }

        if (level.setBlockAndUpdate(proposed, SwarmNestBlocks.NEST_CORE.get().defaultBlockState())) {
            zombie.getData(SwarmAttachments.AGENT_STATE.get()).recordNestFounded();
        }
        proposed = null;
    }

    private boolean baseEligible(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get()
                || !SwarmConfig.NEST_CONSTRUCTION_ENABLED.get()
                || !zombie.isAlive()
                || zombie.isNoAi()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        if (state.targetId() != null || state.hasDestination()
                || zombie.getTarget() != null) {
            return false;
        }
        // Don't modify terrain close to a player, even with mobGriefing on.
        return level.players().stream().noneMatch(
                player -> player.isAlive() && player.distanceToSqr(zombie) <= 64.0
        );
    }

    private BlockPos findBuildSite(ServerLevel level) {
        BlockPos foot = zombie.blockPosition();
        for (int i = 0; i < OFFSETS_X.length; i++) {
            BlockPos candidate = foot.offset(OFFSETS_X[i], 0, OFFSETS_Z[i]);
            if (isNaturalSite(level, candidate)
                    && zombie.distanceToSqr(candidate.getX() + 0.5,
                    candidate.getY() + 0.5, candidate.getZ() + 0.5) >= 3.0) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isNaturalSite(ServerLevel level, BlockPos candidate) {
        if (!level.hasChunkAt(candidate)
                || !level.hasChunkAt(candidate.below())
                || !level.hasChunkAt(candidate.above())) {
            return false;
        }

        var floor = level.getBlockState(candidate.below());
        boolean naturalSoil = floor.is(Blocks.GRASS_BLOCK)
                || floor.is(Blocks.DIRT)
                || floor.is(Blocks.COARSE_DIRT)
                || floor.is(Blocks.ROOTED_DIRT)
                || floor.is(Blocks.PODZOL)
                || floor.is(Blocks.MUD)
                || floor.is(Blocks.MYCELIUM);

        return SwarmNestSitePolicy.naturalFoundation(
                naturalSoil,
                level.getBlockState(candidate).isAir(),
                level.getBlockState(candidate.above()).isAir(),
                !level.getFluidState(candidate).isEmpty()
                        ? false
                        : level.getFluidState(candidate.below()).isEmpty()
        );
    }

    private boolean nearbyNest(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -EXISTING_NEST_RADIUS; dx <= EXISTING_NEST_RADIUS; dx++) {
            for (int dz = -EXISTING_NEST_RADIUS; dz <= EXISTING_NEST_RADIUS; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    probe.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    // Explicit chunk guard: searching cannot trigger worldgen.
                    if (level.hasChunkAt(probe)
                            && level.getBlockState(probe).is(SwarmNestBlocks.NEST_CORE.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
