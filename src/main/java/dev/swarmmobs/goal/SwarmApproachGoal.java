package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Holds the movement channel while a swarm member is outside melee range.
 *
 * Target sensing and planning remain outside the goal. This class only applies the
 * most recent local swarm destination through vanilla PathNavigation.
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

        if (!(zombie.getTarget() instanceof Player target) || !validTarget(target)) {
            return false;
        }

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        if (!state.hasDestination() || state.targetId() == null || !state.targetId().equals(target.getUUID())) {
            return false;
        }

        double release = SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get();
        return zombie.distanceToSqr(target) > release * release;
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
        if (!(zombie.getTarget() instanceof Player target)) {
            return;
        }

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        if (!state.hasDestination()) {
            return;
        }

        zombie.getNavigation().moveTo(
                state.destinationX(),
                target.getY(),
                state.destinationZ(),
                SwarmConfig.MOVE_SPEED.get()
        );
    }

    private static boolean validTarget(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }
}
