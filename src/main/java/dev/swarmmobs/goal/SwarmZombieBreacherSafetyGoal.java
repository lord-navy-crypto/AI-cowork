package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmBreacherSafetyPolicy;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Rare priority-0 movement override while an allied same-target Creeper
 * has actually begun swelling. No artificial explosions, cancelled melee
 * damage, changed mob AI, or remote target lookup. Releases MOVE as soon as
 * the fuse threat disappears and resumes normal combat/engineering Goals.
 */
public final class SwarmZombieBreacherSafetyGoal extends Goal {
    private final Zombie zombie;
    private Creeper hazard;
    private BlockPos retreat;
    private long started;
    private long nextProbe = Long.MIN_VALUE;

    public SwarmZombieBreacherSafetyGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        hazard = null;
        retreat = null;
        if (!(zombie.level() instanceof ServerLevel level)
                || !SwarmConfig.ENABLED.get()
                || !zombie.isAlive() || zombie.isNoAi()
                || !(zombie.getTarget() instanceof Player player)
                || !validTarget(player)) return false;
        SwarmAgentState state=zombie.getData(SwarmAttachments.AGENT_STATE.get());
        UUID targetId=state.targetId();
        if (targetId==null || !targetId.equals(player.getUUID())) return false;
        long tick=level.getGameTime();
        if (nextProbe != Long.MIN_VALUE && tick<nextProbe) return false;
        nextProbe=tick+5+Math.floorMod(zombie.getId(),3);
        for (Creeper other:level.getEntitiesOfClass(
                Creeper.class,zombie.getBoundingBox().inflate(
                        SwarmBreacherSafetyPolicy.ALERT_RANGE),
                candidate -> candidate.isAlive()
                        && (candidate.isIgnited() || candidate.getSwellDir()>0))) {
            SwarmAgentState otherState=other.getData(SwarmAttachments.AGENT_STATE.get());
            if (!SwarmBreacherSafetyPolicy.mustYield(
                    targetId,otherState.targetId(),
                    other.getSwellDir()>0,other.isIgnited(),
                    zombie.distanceToSqr(other))) continue;
            Vec2 vector=SwarmBreacherSafetyPolicy.stepAway(
                    new Vec2(zombie.getX(),zombie.getZ()),
                    new Vec2(other.getX(),other.getZ()),zombie.getUUID().getLeastSignificantBits());
            if (vector==null) continue;
            BlockPos center=BlockPos.containing(vector.x(),zombie.getY(),vector.z());
            // Try alternate modest lateral offsets when the ideal tile is
            // blocked. Never load remote chunks or modify the world.
            for (BlockPos candidate:new BlockPos[]{
                    center,center.north(2),center.south(2),
                    center.east(2),center.west(2)}) {
                if (walkable(level,candidate)
                        && other.distanceToSqr(candidate.getX()+.5,
                                candidate.getY(),candidate.getZ()+.5)
                        > zombie.distanceToSqr(other)+1.0) {
                    hazard=other;
                    retreat=candidate.immutable();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(zombie.level() instanceof ServerLevel level)
                || !SwarmConfig.ENABLED.get()
                || hazard==null || retreat==null
                || !zombie.isAlive() || !hazard.isAlive()
                || !(zombie.getTarget() instanceof Player livePlayer)
                || !validTarget(livePlayer)
                || zombie.getData(SwarmAttachments.AGENT_STATE.get()).targetId()==null
                || !livePlayer.getUUID().equals(
                        zombie.getData(SwarmAttachments.AGENT_STATE.get()).targetId())
                || !SwarmBreacherSafetyPolicy.mustYield(
                        zombie.getData(SwarmAttachments.AGENT_STATE.get()).targetId(),
                        hazard.getData(SwarmAttachments.AGENT_STATE.get()).targetId(),
                        hazard.getSwellDir()>0,hazard.isIgnited(),
                        zombie.distanceToSqr(hazard))
                || level.getGameTime()-started >= 28) return false;
        return zombie.distanceToSqr(retreat.getX()+.5,
                retreat.getY(),retreat.getZ()+.5)>2.25;
    }

    @Override
    public void start() {
        started=zombie.level().getGameTime();
        if (retreat!=null) zombie.getNavigation().moveTo(
                retreat.getX()+.5,retreat.getY(),retreat.getZ()+.5,1.2);
    }

    @Override
    public void tick() {
        if (retreat!=null && zombie.getNavigation().isDone()) {
            zombie.getNavigation().moveTo(
                    retreat.getX()+.5,retreat.getY(),retreat.getZ()+.5,1.2);
        }
    }

    @Override
    public void stop() {
        zombie.getNavigation().stop();
        retreat=null;
        hazard=null;
    }

    private static boolean walkable(ServerLevel level,BlockPos place) {
        if (!level.isInWorldBounds(place)
                || !level.hasChunkAt(place) || !level.hasChunkAt(place.below())) return false;
        return level.getBlockState(place).getCollisionShape(level,place).isEmpty()
                && level.getBlockState(place.above()).getCollisionShape(
                        level,place.above()).isEmpty()
                && level.getBlockState(place.below()).isFaceSturdy(
                        level,place.below(),Direction.UP);
    }

    private static boolean validTarget(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }
}
