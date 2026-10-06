package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmCreeperHandoffPolicy;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

/**
 * High-priority bridge for the vanilla Creeper swell/fuse behavior.
 *
 * The goal stays inactive while the swarm is performing long-range approach,
 * then takes precedence once a directly observed target is inside the normal
 * close-range fuse envelope. Once the fuse is active it continues to yield
 * control away from the swarm approach goal.
 */
public final class SwarmCreeperSwellGoal extends SwellGoal {
    public static final double HANDOFF_DISTANCE = 3.0;

    private final Creeper creeper;

    public SwarmCreeperSwellGoal(Creeper creeper) {
        super(creeper);
        this.creeper = creeper;
    }

    @Override
    public boolean canUse() {
        return handoffActive() && super.canUse();
    }

    private boolean handoffActive() {
        SwarmAgentState state = creeper.getData(SwarmAttachments.AGENT_STATE.get());
        if (creeper.isIgnited() || creeper.getSwellDir() > 0) {
            return true;
        }

        if (!(creeper.getTarget() instanceof Player target)
                || !target.isAlive()
                || target.isSpectator()
                || target.isCreative()
                || state.targetId() == null) {
            return false;
        }

        return SwarmCreeperHandoffPolicy.shouldYieldToSwell(
                state.directObservation(),
                state.targetId().equals(target.getUUID()),
                creeper.hasLineOfSight(target),
                creeper.distanceToSqr(target),
                HANDOFF_DISTANCE,
                creeper.getSwellDir(),
                creeper.isIgnited()
        );
    }
}
