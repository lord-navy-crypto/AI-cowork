package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmRangedHandoffPolicy;
import dev.swarmmobs.config.SwarmConfig;
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
        if (!SwarmConfig.ENABLED.get()) {
            return false;
        }

        SwarmAgentState state = skeleton.getData(SwarmAttachments.AGENT_STATE.get());
        if (!(skeleton.getTarget() instanceof Player target)
                || !target.isAlive()
                || target.isSpectator()
                || target.isCreative()
                || state.targetId() == null) {
            return false;
        }

        // Expire an optional positioning lease before evaluating the
        // high-priority bow Goal, so a failed sidestep cannot lock out
        // vanilla shooting between staggered swarm planner updates.
        state.expireRangedSpacing(skeleton.level().getGameTime());

        // A legal, unobstructed native bow shot takes precedence over
        // optional sidestepping. An old locally planned move must never
        // prevent the actual ranged combat bridge from activating.
        // Ally-blocked lanes and unseen/invalid targets are still refused.
        return state.bowLaneClear()
                && SwarmRangedHandoffPolicy.shouldYieldToVanilla(
                state.directObservation(),
                state.targetId().equals(target.getUUID()),
                skeleton.hasLineOfSight(target),
                skeleton.distanceToSqr(target),
                HANDOFF_DISTANCE
        );
    }
}
