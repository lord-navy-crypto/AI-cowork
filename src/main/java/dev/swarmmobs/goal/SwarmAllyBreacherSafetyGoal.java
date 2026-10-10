package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmBreacherSafetyPolicy;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Locally bounded Minecraft ally safety handoff for Skeletons and Spiders.
 * Only reacts to an actually swelling same-target Creeper in loaded chunks.
 * Never changes the Creeper explosion, player targeting or damage.
 */
public final class SwarmAllyBreacherSafetyGoal extends Goal {
    private final PathfinderMob ally;
    private Creeper hazard;
    private BlockPos waypoint;
    private long started;
    private long nextProbe = Long.MIN_VALUE;

    public SwarmAllyBreacherSafetyGoal(PathfinderMob ally) {
        this.ally = ally;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        hazard = null;
        waypoint = null;
        if (!(ally.level() instanceof ServerLevel level)
                || !SwarmConfig.ENABLED.get()
                || !ally.isAlive() || ally.isNoAi()
                || !(ally.getTarget() instanceof Player target)
                || !validTarget(target)) return false;
        SwarmAgentState state = ally.getData(SwarmAttachments.AGENT_STATE.get());
        UUID targetId = state.targetId();
        if (targetId == null || !targetId.equals(target.getUUID())) return false;
        long now = level.getGameTime();
        if (now < nextProbe) return false;
        nextProbe = now + 5 + Math.floorMod(ally.getId(), 3);

        for (Creeper creeper : level.getEntitiesOfClass(
                Creeper.class,
                ally.getBoundingBox().inflate(SwarmBreacherSafetyPolicy.ALERT_RANGE),
                candidate -> candidate.isAlive()
                        && (candidate.isIgnited() || candidate.getSwellDir() > 0))) {
            SwarmAgentState peer = creeper.getData(SwarmAttachments.AGENT_STATE.get());
            if (!SwarmBreacherSafetyPolicy.mustYield(
                    targetId, peer.targetId(), creeper.getSwellDir() > 0,
                    creeper.isIgnited(), ally.distanceToSqr(creeper))) continue;

            Vec2 delta = SwarmBreacherSafetyPolicy.stepAway(
                    new Vec2(ally.getX(), ally.getZ()),
                    new Vec2(creeper.getX(), creeper.getZ()),
                    ally.getUUID().getLeastSignificantBits());
            if (delta == null) continue;

            BlockPos center = BlockPos.containing(delta.x(), ally.getY(), delta.z());
            for (BlockPos place : new BlockPos[] {
                    center, center.north(2), center.south(2),
                    center.east(2), center.west(2)
            }) {
                if (walkable(level, place)
                        && creeper.distanceToSqr(
                            place.getX()+.5, place.getY(), place.getZ()+.5)
                            > ally.distanceToSqr(creeper) + 1.0) {
                    hazard = creeper;
                    waypoint = place.immutable();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(ally.level() instanceof ServerLevel level)
                || !SwarmConfig.ENABLED.get()
                || hazard == null || waypoint == null
                || !ally.isAlive() || !hazard.isAlive()
                || !(ally.getTarget() instanceof Player target)
                || !validTarget(target)) return false;
        UUID targetId = ally.getData(SwarmAttachments.AGENT_STATE.get()).targetId();
        if (targetId == null || !targetId.equals(target.getUUID())
                || !SwarmBreacherSafetyPolicy.mustYield(targetId,
                    hazard.getData(SwarmAttachments.AGENT_STATE.get()).targetId(),
                    hazard.getSwellDir() > 0, hazard.isIgnited(),
                    ally.distanceToSqr(hazard))
                || level.getGameTime() - started >= 28) return false;
        return ally.distanceToSqr(waypoint.getX()+.5,
                waypoint.getY(), waypoint.getZ()+.5) > 2.25;
    }

    @Override
    public void start() {
        started = ally.level().getGameTime();
        move();
    }

    @Override
    public void tick() {
        if (waypoint != null && ally.getNavigation().isDone()) move();
    }

    private void move() {
        if (waypoint != null) {
            ally.getNavigation().moveTo(waypoint.getX()+.5,
                    waypoint.getY(), waypoint.getZ()+.5, 1.15);
        }
    }

    @Override
    public void stop() {
        ally.getNavigation().stop();
        hazard = null;
        waypoint = null;
    }

    private static boolean walkable(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos)
                && level.hasChunkAt(pos)
                && level.hasChunkAt(pos.below())
                && level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                && level.getBlockState(pos.above())
                    .getCollisionShape(level,pos.above()).isEmpty()
                && level.getBlockState(pos.below())
                    .isFaceSturdy(level,pos.below(),Direction.UP);
    }

    private static boolean validTarget(Player target) {
        return target.isAlive() && !target.isCreative() && !target.isSpectator();
    }
}
