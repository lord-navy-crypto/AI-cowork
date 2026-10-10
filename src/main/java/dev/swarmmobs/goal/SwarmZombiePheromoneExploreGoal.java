package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmNestSurveyBudget;
import dev.swarmmobs.colony.SwarmColonyGatherPolicy;
import dev.swarmmobs.colony.SwarmColonyEmergencePolicy;
import dev.swarmmobs.algorithm.SwarmCongestionPolicy;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.colony.SwarmNestPheromoneField;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.algorithm.SwarmEngagementPolicy;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Minimal self-organization: no source location, route, or world scan is
 * supplied to the worker. It samples the current smell and six nearby cells,
 * takes a single short step up a useful gradient, and yields to higher
 * priority combat, hauling, hunting and harvesting Goals.
 */
public final class SwarmZombiePheromoneExploreGoal extends Goal {
    private static final int STEP = 4;
    private static final int MAX_TICKS = 70;
    private static final double MIN_SCENT = 0.25;
    private static final Map<ServerLevel, SwarmNestSurveyBudget> BUDGETS =
            new WeakHashMap<>();
    private static final int[][] OFFSETS = {
            {STEP,0,0},{-STEP,0,0},{0,0,STEP},{0,0,-STEP},
            {STEP,0,STEP},{-STEP,0,-STEP},{STEP,0,-STEP},{-STEP,0,STEP}
    };
    private final Zombie zombie;
    private BlockPos home;
    private BlockPos destination;
    private long started;
    private long nextSurvey = Long.MIN_VALUE;

    public SwarmZombiePheromoneExploreGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(zombie.level() instanceof ServerLevel level) || !idle(level)) return false;
        long now=level.getGameTime();
        if (nextSurvey != Long.MIN_VALUE && now < nextSurvey) return false;
        nextSurvey = now + 120 + Math.floorMod(zombie.getId(), 49);
        var data=zombie.getPersistentData();
        if (!data.contains("SwarmColonyNest")
                || (data.contains("SwarmColonyDimension")
                && !level.dimension().location().toString()
                        .equals(data.getString("SwarmColonyDimension")))) return false;
        BlockPos core = BlockPos.of(data.getLong("SwarmColonyNest"));
        if (!level.hasChunkAt(core)
                || !level.getBlockState(core).is(SwarmNestBlocks.NEST_CORE.get())
                || !(level.getBlockEntity(core) instanceof SwarmNestBlockEntity nest)
                || zombie.distanceToSqr(core.getX()+.5,core.getY()+.5,core.getZ()+.5)
                        > 23*23) return false;
        if (!BUDGETS.computeIfAbsent(level, key->new SwarmNestSurveyBudget())
                .trySurvey(now, 10)) return false;

        // Reuse the original swarm's geometric congestion policy, taking
        // one short-lived neighbor snapshot rather than scanning per cell.
        var peers=level.getEntitiesOfClass(Zombie.class,
                zombie.getBoundingBox().inflate(8.0),
                other -> other != zombie && other.isAlive()).stream()
                .limit(12).map(other -> new Vec2(other.getX(),other.getZ()))
                .toList();
        BlockPos here=zombie.blockPosition();
        double current=localValue(nest,here,now);
        BlockPos best=null;
        double strongest = Math.max(MIN_SCENT,current+0.18);
        for (int[] offset:OFFSETS) {
            BlockPos candidate=here.offset(offset[0],offset[1],offset[2]);
            if (!level.hasChunkAt(candidate)
                    || candidate.distSqr(core)>24.0*24.0
                    || !level.getBlockState(candidate).getCollisionShape(level,candidate).isEmpty()
                    || !level.getBlockState(candidate.above()).getCollisionShape(
                            level,candidate.above()).isEmpty()
                    || !level.getBlockState(candidate.below()).isFaceSturdy(
                            level,candidate.below(),net.minecraft.core.Direction.UP)) continue;
            // Dense groups spread into nearby alternate branches instead
            // of all following the exact same strongest cell.
            int congestion=SwarmCongestionPolicy.countWithin(peers,
                    new Vec2(candidate.getX()+.5,candidate.getZ()+.5),2.5);
            double strength=localValue(nest,candidate,now)-0.22*congestion;
            if (strength>strongest) { strongest=strength; best=candidate; }
        }
        if (best==null) return false;
        home=core;
        destination=best;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(zombie.level() instanceof ServerLevel level)
                || home==null || destination==null || !idle(level)
                || !level.hasChunkAt(home) || !level.hasChunkAt(destination)
                || !level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get())
                || level.getGameTime()-started>=MAX_TICKS) return false;
        return zombie.distanceToSqr(destination.getX()+.5,
                destination.getY(),destination.getZ()+.5)>2.25;
    }

    @Override
    public void start() {
        started=zombie.level().getGameTime();
        if (destination!=null) {
            zombie.getNavigation().moveTo(destination.getX()+.5,
                    destination.getY(),destination.getZ()+.5,1.0);
        }
    }

    @Override
    public void stop() {
        zombie.getNavigation().stop();
        home=null;
        destination=null;
    }

    private double localValue(SwarmNestBlockEntity nest,BlockPos block,long tick) {
        var point=new SwarmNestPheromoneField.Position(
                block.getX(),block.getY(),block.getZ());
        double best=0;
        for (var kind:new SwarmNestColonyPolicy.Kind[]{
                SwarmNestColonyPolicy.Kind.NUTRIENT,
                SwarmNestColonyPolicy.Kind.TIMBER,
                SwarmNestColonyPolicy.Kind.SOIL}) {
            if (!nest.needsResource(kind)) continue;
            var signal=SwarmNestPheromoneField.signal(kind);
            double scent=SwarmColonyEmergencePolicy.sensedAttraction(
                    zombie.getUUID(),nest.pheromones().scent(point,signal,tick));
            if(kind==SwarmNestColonyPolicy.Kind.NUTRIENT) scent*=1.15;
            best=Math.max(best,scent);
        }
        return best-0.9*nest.pheromones().scent(
                point,SwarmNestPheromoneField.Signal.STOP,tick);
    }

    private boolean idle(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get() || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                || !SwarmConfig.NEST_HAULING_ENABLED.get()
                || !SwarmConfig.NEST_PHEROMONES_ENABLED.get()
                || !SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !zombie.isAlive() || zombie.isNoAi()
                || zombie.getTarget()!=null) return false;
        SwarmAgentState state=zombie.getData(SwarmAttachments.AGENT_STATE.get());
        return state.targetId()==null && !state.hasDestination()
                && SwarmEngagementPolicy.canDoNestWork(state.engagementMode());
    }
}
