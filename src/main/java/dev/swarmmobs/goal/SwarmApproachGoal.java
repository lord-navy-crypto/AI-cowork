package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Holds the movement channel while a swarm member is outside the vanilla melee
 * release radius.
 *
 * Indirect target memory uses the last-known observation snapshot, not a live player
 * position. As confidence decays, movement speed becomes more conservative.
 */
public final class SwarmApproachGoal extends Goal {
    private final Zombie zombie;

    public SwarmApproachGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SwarmConfig.ENABLED.get()) {
            return false;
        }

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        if (!state.hasDestination() || state.targetId() == null) {
            return false;
        }

        double release = SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get();

        if (state.directObservation()
                && zombie.getTarget() instanceof Player target
                && validTarget(target)
                && state.targetId().equals(target.getUUID())) {
            return zombie.distanceToSqr(target) > release * release;
        }

        TargetObservation observation = state.targetObservation();
        if (observation == null || !observation.hasFinitePosition()) {
            return false;
        }

        return zombie.distanceToSqr(
                observation.x(),
                observation.y(),
                observation.z()
        ) > release * release;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        moveToLatestPlan();
    }

    @Override
    public void tick() {
        if (zombie.tickCount % 3 == 0 || zombie.getNavigation().isDone()) {
            moveToLatestPlan();
        }
    }

    @Override
    public void stop() {
        zombie.getNavigation().stop();
    }

    private void moveToLatestPlan() {
        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        if (!state.hasDestination()) {
            return;
        }

        double targetY = zombie.getY();
        if (state.directObservation() && zombie.getTarget() instanceof Player target) {
            targetY = target.getY();
        } else {
            TargetObservation observation = state.targetObservation();
            if (observation != null && observation.hasFinitePosition()) {
                targetY = observation.y();
            }
        }

        double confidence = 1.0;
        if (zombie.level() instanceof ServerLevel level) {
            confidence = state.targetConfidence(
                    level.getGameTime(),
                    SwarmConfig.TARGET_MEMORY_TICKS.get()
            );
        }

        double minFactor = SwarmConfig.STALE_TARGET_MIN_SPEED_FACTOR.get();
        double speedFactor = minFactor + (1.0 - minFactor) * confidence;
        double speed = SwarmConfig.MOVE_SPEED.get() * speedFactor;

        zombie.getNavigation().moveTo(
                state.destinationX(),
                targetY,
                state.destinationZ(),
                speed
        );
    }

    private static boolean validTarget(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }
}
