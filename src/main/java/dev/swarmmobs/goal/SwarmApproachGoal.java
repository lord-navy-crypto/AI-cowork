package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentProfile;
import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Owns movement while a supported swarm member is repositioning.
 *
 * Assault agents yield near the target so vanilla melee can dominate.
 * Ranged-support agents yield when they reach their planned support destination so
 * vanilla ranged-combat goals can take over.
 */
public final class SwarmApproachGoal extends Goal {
    private final PathfinderMob mob;

    public SwarmApproachGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SwarmConfig.ENABLED.get() || !SwarmAgentProfiles.isSupported(mob)) {
            return false;
        }

        SwarmAgentState state = mob.getData(SwarmAttachments.AGENT_STATE.get());
        if (!state.hasDestination() || state.targetId() == null) {
            return false;
        }

        if (state.behaviorMode() == SwarmBehaviorMode.SEARCH) {
            double tolerance = SwarmConfig.SEARCH_ARRIVAL_TOLERANCE.get();
            return mob.distanceToSqr(
                    state.destinationX(),
                    mob.getY(),
                    state.destinationZ()
            ) > tolerance * tolerance;
        }

        SwarmAgentProfile profile = SwarmAgentProfiles.profile(mob);
        if (profile.archetype() == SwarmAgentArchetype.RANGED_SUPPORT) {
            double tolerance = Math.max(0.5, profile.arrivalTolerance());
            return mob.distanceToSqr(
                    state.destinationX(),
                    mob.getY(),
                    state.destinationZ()
            ) > tolerance * tolerance;
        }

        double release = SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get();

        if (state.directObservation()
                && mob.getTarget() instanceof Player target
                && validTarget(target)
                && state.targetId().equals(target.getUUID())) {
            return mob.distanceToSqr(target) > release * release;
        }

        TargetObservation observation = state.targetObservation();
        if (observation == null || !observation.hasFinitePosition()) {
            return false;
        }

        return mob.distanceToSqr(
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
        if (mob.tickCount % 3 == 0 || mob.getNavigation().isDone()) {
            moveToLatestPlan();
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    private void moveToLatestPlan() {
        SwarmAgentState state = mob.getData(SwarmAttachments.AGENT_STATE.get());
        if (!state.hasDestination()) {
            return;
        }

        double targetY = mob.getY();
        if (state.directObservation() && mob.getTarget() instanceof Player target) {
            targetY = target.getY();
        } else {
            TargetObservation observation = state.targetObservation();
            if (observation != null && observation.hasFinitePosition()) {
                targetY = observation.y();
            }
        }

        double confidence = 1.0;
        if (mob.level() instanceof ServerLevel level) {
            confidence = state.targetConfidence(
                    level.getGameTime(),
                    SwarmConfig.TARGET_MEMORY_TICKS.get()
            );
        }

        SwarmAgentProfile profile = SwarmAgentProfiles.profile(mob);
        double minFactor = SwarmConfig.STALE_TARGET_MIN_SPEED_FACTOR.get();
        double confidenceFactor = minFactor + (1.0 - minFactor) * confidence;
        double speed = SwarmConfig.MOVE_SPEED.get()
                * profile.moveSpeedMultiplier()
                * confidenceFactor;

        mob.getNavigation().moveTo(
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
