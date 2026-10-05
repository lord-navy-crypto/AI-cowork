package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentProfile;
import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.algorithm.SwarmMovementPolicy;
import dev.swarmmobs.algorithm.SwarmNavigationRecoveryPolicy;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
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
    private double progressSampleX;
    private double progressSampleZ;
    private long progressSampleTick = Long.MIN_VALUE;
    private boolean recoveryActive;
    private double recoveryX;
    private double recoveryZ;
    private long recoveryUntilTick = Long.MIN_VALUE;

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
        resetProgressSample();
        moveToLatestPlan();
    }

    @Override
    public void tick() {
        updateRecoveryState();

        if (mob.tickCount % 3 == 0 || mob.getNavigation().isDone()) {
            moveToLatestPlan();
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        recoveryActive = false;
        recoveryUntilTick = Long.MIN_VALUE;
        progressSampleTick = Long.MIN_VALUE;
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
        double behaviorSpeedFactor = SwarmMovementPolicy.speedFactor(
                state.behaviorMode(),
                confidence,
                SwarmConfig.STALE_TARGET_MIN_SPEED_FACTOR.get(),
                SwarmConfig.SEARCH_SPEED_FACTOR.get()
        );
        double speed = SwarmConfig.MOVE_SPEED.get()
                * profile.moveSpeedMultiplier()
                * behaviorSpeedFactor;

        double navigationX = recoveryActive ? recoveryX : state.destinationX();
        double navigationZ = recoveryActive ? recoveryZ : state.destinationZ();

        mob.getNavigation().moveTo(
                navigationX,
                targetY,
                navigationZ,
                speed
        );
    }

    private void updateRecoveryState() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTick = level.getGameTime();
        if (recoveryActive) {
            if (gameTick >= recoveryUntilTick) {
                recoveryActive = false;
                resetProgressSample();
            }
            return;
        }

        if (progressSampleTick == Long.MIN_VALUE) {
            resetProgressSample();
            return;
        }

        long elapsed = gameTick - progressSampleTick;
        if (elapsed < SwarmConfig.NAV_STUCK_WINDOW_TICKS.get()) {
            return;
        }

        double moved = Math.hypot(
                mob.getX() - progressSampleX,
                mob.getZ() - progressSampleZ
        );

        SwarmAgentState state = mob.getData(SwarmAttachments.AGENT_STATE.get());
        if (!state.hasDestination()) {
            resetProgressSample();
            return;
        }

        double remaining = Math.hypot(
                state.destinationX() - mob.getX(),
                state.destinationZ() - mob.getZ()
        );

        if (moved < SwarmConfig.NAV_STUCK_MIN_PROGRESS.get()
                && remaining > Math.max(1.5, SwarmConfig.NAV_STUCK_MIN_PROGRESS.get() * 2.0)) {
            var recovery = SwarmNavigationRecoveryPolicy.recoveryWaypoint(
                    new Vec2(mob.getX(), mob.getZ()),
                    new Vec2(state.destinationX(), state.destinationZ()),
                    mob.getId(),
                    SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.get()
            );

            if (recovery.active()) {
                recoveryActive = true;
                recoveryX = recovery.waypoint().x();
                recoveryZ = recovery.waypoint().z();
                recoveryUntilTick = gameTick + SwarmConfig.NAV_RECOVERY_DURATION_TICKS.get();
                mob.getNavigation().stop();
            }
        }

        resetProgressSample();
    }

    private void resetProgressSample() {
        progressSampleX = mob.getX();
        progressSampleZ = mob.getZ();
        if (mob.level() instanceof ServerLevel level) {
            progressSampleTick = level.getGameTime();
        } else {
            progressSampleTick = Long.MIN_VALUE;
        }
    }

    private static boolean validTarget(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }
}
