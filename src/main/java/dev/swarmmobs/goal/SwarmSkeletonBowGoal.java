package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmRangedHandoffPolicy;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;

/**
 * High-priority ranged-combat bridge used only inside the direct visible bow
 * envelope. Outside that envelope the goal stays inactive so the swarm
 * movement planner can reposition the Skeleton.
 */
public final class SwarmSkeletonBowGoal extends RangedBowAttackGoal<Skeleton> {
    public static final double HANDOFF_DISTANCE = 15.0;

    private final Skeleton skeleton;

    public SwarmSkeletonBowGoal(Skeleton skeleton) {
        super(skeleton, 1.0, 20, (float) HANDOFF_DISTANCE);
        this.skeleton = skeleton;
    }

    @Override
    public boolean canUse() {
        return handoffActive() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return handoffActive() && super.canContinueToUse();
    }

    private boolean handoffActive() {
        SwarmAgentState state = skeleton.getData(SwarmAttachments.AGENT_STATE.get());
        if (!(skeleton.getTarget() instanceof Player target)
                || !target.isAlive()
                || target.isSpectator()
                || target.isCreative()
                || state.targetId() == null) {
            return false;
        }

        return SwarmRangedHandoffPolicy.shouldYieldToVanilla(
                state.directObservation(),
                state.targetId().equals(target.getUUID()),
                skeleton.hasLineOfSight(target),
                skeleton.distanceToSqr(target),
                HANDOFF_DISTANCE
        );
    }
}
