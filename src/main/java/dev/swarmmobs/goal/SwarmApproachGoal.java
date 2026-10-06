package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentProfile;
import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.agent.SwarmNavigationMode;
import dev.swarmmobs.algorithm.SwarmMovementPolicy;
import dev.swarmmobs.algorithm.SwarmLocalPlannerPolicy;
import dev.swarmmobs.algorithm.SwarmNavigationRecoveryPolicy;
import dev.swarmmobs.algorithm.SwarmObstacleAvoidancePolicy;
import dev.swarmmobs.algorithm.SwarmObstacleHoldPolicy;
import dev.swarmmobs.algorithm.SwarmTerrainSupportPolicy;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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
    private boolean obstacleDetourActive;
    private double obstacleDetourX;
    private double obstacleDetourZ;
    private long obstacleDetourUntilTick = Long.MIN_VALUE;

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
        obstacleDetourActive = false;
        obstacleDetourUntilTick = Long.MIN_VALUE;
        mob.getData(SwarmAttachments.AGENT_STATE.get()).clearNavigationTelemetry();
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

        if (!recoveryActive && mob.level() instanceof ServerLevel level) {
            long gameTick = level.getGameTime();

            if (obstacleDetourActive) {
                double detourDistance = Math.hypot(
                        obstacleDetourX - mob.getX(),
                        obstacleDetourZ - mob.getZ()
                );

                obstacleDetourActive = SwarmObstacleHoldPolicy.shouldKeepDetour(
                        gameTick,
                        obstacleDetourUntilTick,
                        detourDistance,
                        SwarmConfig.NAV_OBSTACLE_ARRIVAL_TOLERANCE.get()
                );

                if (obstacleDetourActive) {
                    navigationX = obstacleDetourX;
                    navigationZ = obstacleDetourZ;
                }
            }

            if (!obstacleDetourActive && SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.get()) {
                var avoidance = localObstacleAvoidance(
                        level,
                        new Vec2(mob.getX(), mob.getZ()),
                        new Vec2(state.destinationX(), state.destinationZ())
                );
                if (avoidance.active()) {
                    obstacleDetourActive = true;
                    obstacleDetourX = avoidance.waypoint().x();
                    obstacleDetourZ = avoidance.waypoint().z();
                    obstacleDetourUntilTick = SwarmObstacleHoldPolicy.holdUntil(
                            gameTick,
                            SwarmConfig.NAV_OBSTACLE_HOLD_TICKS.get()
                    );
                    navigationX = obstacleDetourX;
                    navigationZ = obstacleDetourZ;
                }
            }
        }

        SwarmNavigationMode navigationMode = recoveryActive
                ? SwarmNavigationMode.RECOVERY
                : obstacleDetourActive
                        ? SwarmNavigationMode.OBSTACLE_DETOUR
                        : SwarmNavigationMode.PLAN;

        state.updateNavigationTelemetry(
                navigationMode,
                navigationX,
                navigationZ,
                false
        );

        mob.getNavigation().moveTo(
                navigationX,
                targetY,
                navigationZ,
                speed
        );
    }

    private SwarmObstacleAvoidancePolicy.Avoidance localObstacleAvoidance(
            ServerLevel level,
            Vec2 self,
            Vec2 destination
    ) {
        Vec2 toTarget = destination.subtract(self);
        double distance = toTarget.length();
        if (distance < 1.0e-9) {
            return new SwarmObstacleAvoidancePolicy.Avoidance(destination, false, 0);
        }

        double lookahead = SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.get();
        double lateralDistance = SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.get();
        Vec2 forward = toTarget.scale(1.0 / distance);
        Vec2 left = new Vec2(-forward.z(), forward.x());

        Vec2 frontProbe = self.add(forward.scale(Math.min(distance, lookahead)));
        boolean frontBlocked = isProbeBlocked(level, frontProbe.x(), frontProbe.z());
        if (!frontBlocked) {
            return new SwarmObstacleAvoidancePolicy.Avoidance(destination, false, 0);
        }

        double[] lateralScales = {0.75, -0.75, 1.50, -1.50};
        List<SwarmLocalPlannerPolicy.Candidate> candidates = new ArrayList<>();

        for (double scale : lateralScales) {
            double lateralOffset = lateralDistance * scale;
            Vec2 candidate = frontProbe.add(left.scale(lateralOffset));
            boolean blocked = isProbeBlocked(level, candidate.x(), candidate.z());
            double congestion = localCongestion(level, candidate);

            candidates.add(new SwarmLocalPlannerPolicy.Candidate(
                    candidate,
                    blocked,
                    lateralOffset,
                    congestion
            ));
        }

        SwarmLocalPlannerPolicy.Choice choice = SwarmLocalPlannerPolicy.choose(
                self,
                destination,
                candidates,
                SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.get(),
                SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.get(),
                SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.get()
        );

        if (!choice.active()) {
            return new SwarmObstacleAvoidancePolicy.Avoidance(destination, false, 0);
        }

        int side = choice.waypoint().subtract(frontProbe).dot(left) >= 0.0 ? 1 : -1;
        return new SwarmObstacleAvoidancePolicy.Avoidance(choice.waypoint(), true, side);
    }

    private double localCongestion(ServerLevel level, Vec2 candidate) {
        double radius = SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.get();
        double radiusSqr = radius * radius;

        return level.getEntitiesOfClass(
                        PathfinderMob.class,
                        mob.getBoundingBox().inflate(radius + SwarmConfig.NAV_OBSTACLE_LATERAL_DISTANCE.get()),
                        peer -> peer != mob
                                && peer.isAlive()
                                && SwarmAgentProfiles.isSupported(peer)
                ).stream()
                .filter(peer -> {
                    double dx = peer.getX() - candidate.x();
                    double dz = peer.getZ() - candidate.z();
                    return dx * dx + dz * dz <= radiusSqr;
                })
                .count();
    }

    private boolean isProbeBlocked(ServerLevel level, double x, double z) {
        int minY = (int) Math.floor(mob.getY());
        int maxY = Math.max(minY, (int) Math.floor(mob.getY() + mob.getBbHeight() - 0.01));

        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }

        if (!SwarmConfig.NAV_WALKABILITY_ENABLED.get()) {
            return false;
        }

        BlockPos footPos = BlockPos.containing(x, minY, z);
        if (level.getFluidState(footPos).is(FluidTags.WATER)) {
            return false;
        }

        int maxDrop = SwarmConfig.NAV_MAX_PROBE_DROP_BLOCKS.get();
        boolean[] supportByDropDepth = new boolean[maxDrop + 1];
        for (int depth = 0; depth <= maxDrop; depth++) {
            BlockPos supportPos = BlockPos.containing(x, minY - 1 - depth, z);
            supportByDropDepth[depth] =
                    !level.getBlockState(supportPos).getCollisionShape(level, supportPos).isEmpty()
                            || level.getFluidState(supportPos).is(FluidTags.WATER);
        }

        return !SwarmTerrainSupportPolicy.hasSupport(
                supportByDropDepth,
                maxDrop
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
                obstacleDetourActive = false;
                obstacleDetourUntilTick = Long.MIN_VALUE;
                recoveryActive = true;
                recoveryX = recovery.waypoint().x();
                recoveryZ = recovery.waypoint().z();
                state.updateNavigationTelemetry(
                        SwarmNavigationMode.RECOVERY,
                        recoveryX,
                        recoveryZ,
                        true
                );
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
