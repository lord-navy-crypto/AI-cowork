package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmNestSurveyBudget;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.colony.SwarmNestHaulPolicy;
import dev.swarmmobs.colony.SwarmNestScoutSignal;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.GameRules;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Passive opt-in scout. A roaming Spider notices a handful of physical
 * resource drops and stamps a short-lived nest-local observation on them.
 * It does not steer the Spider, teleport cargo, use inventories or load
 * chunks. Vanilla wandering supplies actual scout movement.
 *
 * Per-Spider cooldown plus a per-dimension survey budget cap query cost.
 */
public final class SwarmSpiderColonyScoutGoal extends Goal {
    private static final int SURVEY_RADIUS = 12;
    private static final int MAX_MARKS_PER_SURVEY = 8;
    private static final int SURVEY_INTERVAL_TICKS = 160;
    private static final Map<ServerLevel, SwarmNestSurveyBudget> BUDGETS =
            new WeakHashMap<>();

    private final Spider spider;
    private long nextSurveyTick = Long.MIN_VALUE;
    private BlockPos home;

    public SwarmSpiderColonyScoutGoal(Spider spider) {
        this.spider = spider;
        // No MOVE or LOOK flags: this must never replace vanilla combat,
        // wandering or the swarm's movement controller.
    }

    @Override
    public boolean canUse() {
        home = null;
        if (!(spider.level() instanceof ServerLevel level) || !idle(level)) return false;
        long now = level.getGameTime();
        if (nextSurveyTick != Long.MIN_VALUE && now < nextSurveyTick) return false;
        nextSurveyTick = now + SURVEY_INTERVAL_TICKS
                + Math.floorMod(spider.getId(), 37);

        var data = spider.getPersistentData();
        if (!data.contains("SwarmColonyNest")
                || (data.contains("SwarmColonyDimension")
                        && !level.dimension().location().toString()
                                .equals(data.getString("SwarmColonyDimension")))) {
            return false;
        }
        BlockPos candidate = BlockPos.of(data.getLong("SwarmColonyNest"));
        if (!level.hasChunkAt(candidate)
                || !level.getBlockState(candidate).is(SwarmNestBlocks.NEST_CORE.get())
                || !(level.getBlockEntity(candidate) instanceof SwarmNestBlockEntity)
                || spider.distanceToSqr(candidate.getX() + .5,
                        candidate.getY() + .5, candidate.getZ() + .5) > 24.0 * 24.0) {
            return false;
        }
        if (!BUDGETS.computeIfAbsent(level, ignored -> new SwarmNestSurveyBudget())
                .trySurvey(now, 8)) {
            return false;
        }
        home = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return false; // One bounded survey, never a persistent navigation goal.
    }

    @Override
    public void start() {
        if (!(spider.level() instanceof ServerLevel level)
                || home == null || !idle(level)
                || !level.hasChunkAt(home)
                || !level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get())
                || !(level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest)) {
            home = null;
            return;
        }
        int marked = 0;
        long now = level.getGameTime();
        for (ItemEntity item : level.getEntitiesOfClass(
                ItemEntity.class, spider.getBoundingBox().inflate(SURVEY_RADIUS),
                candidate -> candidate.isAlive() && !candidate.getItem().isEmpty())) {
            var kind = SwarmNestBlockEntity.classify(item.getItem());
            if (kind == SwarmNestColonyPolicy.Kind.NONE
                    || item.getItem().getCount() > SwarmConfig.NEST_HAUL_MAX_STACK.get()
                    || !SwarmNestHaulPolicy.hasRoomFor(
                            nest.resources(), item.getItem().getCount(), kind)
                    || item.distanceToSqr(home.getX() + .5,
                            home.getY() + .5, home.getZ() + .5) < 16.0) {
                continue; // local passive intake already handles core-adjacent drops
            }
            // Deliver the sighting to the loaded Nest Core. This shares
            // distant resource coordinates with its workers, without an
            // ItemEntity reference or an invented inventory stack.
            if (!nest.reportScoutItem(item, now)) continue;
            SwarmNestScoutSignal.mark(item, home, now);
            if (++marked >= MAX_MARKS_PER_SURVEY) break;
        }
        home = null;
    }

    private boolean idle(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get() || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                || !SwarmConfig.NEST_HAULING_ENABLED.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !spider.isAlive() || spider.isNoAi() || spider.getTarget() != null) {
            return false;
        }
        SwarmAgentState state = spider.getData(SwarmAttachments.AGENT_STATE.get());
        return state.targetId() == null && !state.hasDestination();
    }
}
