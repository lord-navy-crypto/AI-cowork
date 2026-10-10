package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentProfile;
import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.agent.SwarmNavigationMode;
import dev.swarmmobs.agent.SwarmPlannerContext;
import dev.swarmmobs.algorithm.SwarmCongestionPolicy;
import dev.swarmmobs.algorithm.SwarmPathBudgetRegistry;
import dev.swarmmobs.algorithm.SwarmPathProbePolicy;
import dev.swarmmobs.algorithm.SwarmNavigationCommandPolicy;
import dev.swarmmobs.algorithm.SwarmNavigationEpisodePolicy;
import dev.swarmmobs.algorithm.SwarmNavigationCommandTelemetry;
import dev.swarmmobs.algorithm.SwarmMovementPolicy;
import dev.swarmmobs.algorithm.SwarmLocalPlannerPolicy;
import dev.swarmmobs.algorithm.SwarmPathEvidencePolicy;
import dev.swarmmobs.algorithm.SwarmNavigationRecoveryPolicy;
import dev.swarmmobs.algorithm.SwarmRecoveryCandidatePolicy;
import dev.swarmmobs.algorithm.SwarmRangedHandoffPolicy;
import dev.swarmmobs.algorithm.SwarmZombieFlankHandoffPolicy;
import dev.swarmmobs.algorithm.SwarmCreeperHandoffPolicy;
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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Owns movement while a supported swarm member is repositioning.
 *
 * Assault agents yield near the target so vanilla melee can dominate.
 * Ranged-support agents yield when they reach their planned support destination so
 * vanilla ranged-combat goals can take over.
 */
public final class SwarmApproachGoal extends Goal {
    // Combat bridges own priority 0 only inside their bounded engagement
    // envelopes; the swarm MOVE goal remains responsible for approach outside.
    private final PathfinderMob mob;
    private double progressSampleX;
    private double progressSampleZ;
    private long progressSampleTick = Long.MIN_VALUE;
    private boolean recoveryActive;
    private double recoveryX;
    private double recoveryZ;
    private long recoveryUntilTick = Long.MIN_VALUE;
    private double recoveryPlanAnchorX;
    private double recoveryPlanAnchorZ;
    private UUID activeEpisodeTarget;
    private SwarmBehaviorMode activeEpisodeMode;
    private boolean activeEpisodeKnown;
    private boolean commandIssued;
    private long lastCommandTick = Long.MIN_VALUE;
    private double lastCommandX;
    private double lastCommandY;
    private double lastCommandZ;
    private double lastCommandSpeed;
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

        if (mob instanceof Creeper creeper
                && creeper.getTarget() instanceof Player target
                && validTarget(target)
                && state.targetId().equals(target.getUUID())
                && SwarmCreeperHandoffPolicy.shouldYieldToSwell(
                        state.directObservation(),
                        true,
                        creeper.hasLineOfSight(target),
                        creeper.distanceToSqr(target),
                        SwarmCreeperSwellGoal.HANDOFF_DISTANCE,
                        creeper.getSwellDir(),
                        creeper.isIgnited()
                )) {
            return false;
        }

        SwarmAgentProfile profile = SwarmAgentProfiles.profile(mob);
        if (profile.archetype() == SwarmAgentArchetype.RANGED_SUPPORT) {
            if (state.rangedSpacingActive()) {
                return mob.distanceToSqr(state.destinationX(), mob.getY(),
                        state.destinationZ()) > 0.75 * 0.75;
            }
            if (mob.getTarget() instanceof Player target
                    && validTarget(target)
                    && state.bowLaneClear()
                    && SwarmRangedHandoffPolicy.shouldYieldToVanilla(
                            state.directObservation(),
                            state.targetId().equals(target.getUUID()),
                            mob.hasLineOfSight(target),
                            mob.distanceToSqr(target),
                            SwarmSkeletonBowGoal.HANDOFF_DISTANCE
                    )) {
                return false;
            }

            double tolerance = Math.max(0.5, profile.arrivalTolerance());
            return mob.distanceToSqr(
                    state.destinationX(),
                    mob.getY(),
                    state.destinationZ()
            ) > tolerance * tolerance;
        }

        // Creepers are not melee attackers: keep swarm movement active until the
        // exact fuse handoff boundary. Using the generic melee release distance
        // here creates a dead zone where MOVE can yield before SwellGoal is eligible.
        double release = mob instanceof Creeper
                ? SwarmCreeperSwellGoal.HANDOFF_DISTANCE
                : SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get();

        if (mob.getTarget() instanceof Player target
                && validTarget(target)
                && state.targetId().equals(target.getUUID())) {
            if (!mob.hasLineOfSight(target)) {
                return true;
            }
            if (state.directObservation()) {
                // Flank-assigned Zombies get a small local completion window
                // before vanilla melee takes over. At <=2.5 blocks, melee
                // ALWAYS wins so game tactical movement cannot disable attacks.
                if (mob instanceof net.minecraft.world.entity.monster.Zombie
                        && SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                            state.role(), true, true,
                            mob.distanceToSqr(target),
                            mob.distanceToSqr(state.destinationX(), mob.getY(),
                                    state.destinationZ()),
                            release)) {
                    return true;
                }
                return mob.distanceToSqr(target) > release * release;
            }
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
        // A reused Goal object may have finished under another target.
        activeEpisodeKnown = false;
        refreshMovementEpisode();
        commandIssued = false;
        resetProgressSample();
        moveToLatestPlan();
    }

    @Override
    public void tick() {
        refreshMovementEpisode();
        updateRecoveryState();

        if (mob.tickCount % 3 == 0 || mob.getNavigation().isDone()) {
            moveToLatestPlan();
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (mob.level() instanceof ServerLevel level) {
            SwarmPathBudgetRegistry.cancel(level, mob);
        }
        recoveryActive = false;
        recoveryUntilTick = Long.MIN_VALUE;
        obstacleDetourActive = false;
        obstacleDetourUntilTick = Long.MIN_VALUE;
        activeEpisodeKnown = false;
        activeEpisodeTarget = null;
        activeEpisodeMode = null;
        mob.getData(SwarmAttachments.AGENT_STATE.get()).clearNavigationTelemetry();
        progressSampleTick = Long.MIN_VALUE;
        commandIssued = false;
        lastCommandTick = Long.MIN_VALUE;
    }

    /**
     * The Goal may remain selected while the target ID or SEARCH/ENGAGE mode
     * changes. Retaining old recovery waypoints can keep an NPC following
     * an obsolete route.
     */
    private void refreshMovementEpisode() {
        SwarmAgentState state = mob.getData(SwarmAttachments.AGENT_STATE.get());
        UUID target = state.targetId();
        SwarmBehaviorMode mode = state.behaviorMode();
        boolean changed = SwarmNavigationEpisodePolicy.changed(
                activeEpisodeKnown, activeEpisodeTarget, activeEpisodeMode,
                target, mode);
        if (changed) {
            clearObsoleteNavigation(state);
            state.recordNavigationEpisodeReset();
        }
        activeEpisodeTarget = target;
        activeEpisodeMode = mode;
        activeEpisodeKnown = true;
    }

    private void clearObsoleteNavigation(SwarmAgentState state) {
        recoveryActive = false;
        recoveryUntilTick = Long.MIN_VALUE;
        obstacleDetourActive = false;
        obstacleDetourUntilTick = Long.MIN_VALUE;
        commandIssued = false;
        lastCommandTick = Long.MIN_VALUE;
        mob.getNavigation().stop();
        if (mob.level() instanceof ServerLevel level) {
            SwarmPathBudgetRegistry.cancel(level, mob);
        }
        state.clearNavigationTelemetry();
        resetProgressSample();
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

            if (!SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.get()
                    && obstacleDetourActive) {
                obstacleDetourActive = false;
                obstacleDetourUntilTick = Long.MIN_VALUE;
            }

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
                if (avoidance == null) {
                    // Evidence budget denied this entire detour episode.
                    // Preserve the existing movement command; submitting a
                    // direct unverified path would defeat the quota.
                    return;
                }
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

        if (mob.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            SwarmNavigationCommandPolicy.Decision decision =
                    SwarmNavigationCommandPolicy.evaluate(
                            commandIssued,
                            mob.getNavigation().isDone(),
                            now,
                            lastCommandTick,
                            navigationX,
                            targetY,
                            navigationZ,
                            speed,
                            lastCommandX,
                            lastCommandY,
                            lastCommandZ,
                            lastCommandSpeed
                    );
            SwarmNavigationCommandTelemetry.record(level, decision);
            if (decision == SwarmNavigationCommandPolicy.Decision.SKIP) {
                return;
            }
            lastCommandTick = now;
        }

        // Only meaningful changes and bounded retries create a new path.
        // Active combat goals still retain their original MOVE handoff.
        commandIssued = true;
        lastCommandX = navigationX;
        lastCommandY = targetY;
        lastCommandZ = navigationZ;
        lastCommandSpeed = speed;
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
            SwarmPathBudgetRegistry.cancel(level, mob);
            return new SwarmObstacleAvoidancePolicy.Avoidance(destination, false, 0);
        }

        double[] lateralScales = {0.75, -0.75, 1.50, -1.50};
        List<Vec2> candidatePoints = new ArrayList<>(lateralScales.length);
        boolean[] blockedCandidates = new boolean[lateralScales.length];
        for (int i = 0; i < lateralScales.length; i++) {
            Vec2 candidate = frontProbe.add(left.scale(lateralDistance * lateralScales[i]));
            candidatePoints.add(candidate);
            blockedCandidates[i] = isProbeBlocked(level, candidate.x(), candidate.z());
        }
        // A blocked candidate never calls createPath. Reserving all four slots
        // wastes the shared per-tick budget and can starve viable candidates.
        int requiredPathQueries = SwarmPathProbePolicy.requiredQueries(
                blockedCandidates, SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get()
        );
        if (requiredPathQueries > 0
                && !SwarmPathBudgetRegistry.reserve(level, mob, requiredPathQueries)) {
            // Unknown evidence is neither a valid path nor a failed path.
            // Returning null means "deferred", not "use direct route".
            mob.getData(SwarmAttachments.AGENT_STATE.get()).clearPlannerTelemetry();
            return null;
        }
        if (requiredPathQueries == 0) {
            SwarmPathBudgetRegistry.cancel(level, mob);
        }
        // No open candidate can be chosen if the entire detour is blocked.
        // Avoid an additional entity-index scan in that common wall case.
        // Otherwise one peer snapshot covers all candidates.
        if (requiredPathQueries == 0) {
            SwarmPathBudgetRegistry.cancel(level, mob);
        }
        List<Vec2> congestionPeers = SwarmPathProbePolicy.requiredQueries(blockedCandidates, true) == 0
                ? List.of()
                : findCongestionPeers(level, self, candidatePoints);
        double congestionRadius = SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.get();
        List<SwarmLocalPlannerPolicy.Candidate> candidates = new ArrayList<>();
        int blockedCount = 0;
        int unreachableCount = 0;
        int pathQueries = 0;

        for (int i = 0; i < lateralScales.length; i++) {
            double lateralOffset = lateralDistance * lateralScales[i];
            Vec2 candidate = candidatePoints.get(i);
            boolean blocked = blockedCandidates[i];
            if (blocked) blockedCount++;
            double congestion = SwarmCongestionPolicy.countWithin(congestionPeers, candidate, congestionRadius);

            boolean pathReachable = true;
            int pathNodeCount = 0;
            double pathResidualDistance = 0.0;

            if (!blocked && SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get()) {
                pathQueries++;
                var path = mob.getNavigation().createPath(
                        BlockPos.containing(candidate.x(), mob.getY(), candidate.z()),
                        0
                );

                if (path != null) {
                    pathNodeCount = path.getNodeCount();
                    pathResidualDistance = path.getDistToTarget();
                } else {
                    pathResidualDistance = Double.POSITIVE_INFINITY;
                }
                pathReachable = SwarmPathEvidencePolicy.acceptable(
                        path != null,
                        path != null && path.canReach(),
                        pathResidualDistance,
                        SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.get()
                );
                if (!pathReachable) unreachableCount++;
            }

            candidates.add(new SwarmLocalPlannerPolicy.Candidate(
                    candidate,
                    blocked,
                    lateralOffset,
                    congestion,
                    pathReachable,
                    pathNodeCount,
                    pathResidualDistance
            ));
        }

        SwarmLocalPlannerPolicy.Choice choice = SwarmLocalPlannerPolicy.choose(
                self,
                destination,
                candidates,
                SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.get(),
                SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.get(),
                SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.get(),
                SwarmConfig.NAV_PATH_NODE_PENALTY.get(),
                SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.get()
        );

        mob.getData(SwarmAttachments.AGENT_STATE.get()).updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                candidates.size(),
                blockedCount,
                unreachableCount,
                Math.max(0, candidates.size() - blockedCount - unreachableCount),
                choice.candidateIndex(),
                choice.score(),
                pathQueries
        );

        if (!choice.active()) {
            return new SwarmObstacleAvoidancePolicy.Avoidance(destination, false, 0);
        }

        Vec2 lateralDelta = choice.waypoint().subtract(frontProbe);
        double lateralProjection = lateralDelta.x() * left.x() + lateralDelta.z() * left.z();
        int side = lateralProjection >= 0.0 ? 1 : -1;
        return new SwarmObstacleAvoidancePolicy.Avoidance(choice.waypoint(), true, side);
    }

    /**
     * One conservative entity-index scan covers every candidate in this local
     * planning episode. Capturing positions also guarantees each candidate is
     * scored against the same peer snapshot, preventing intra-plan jitter.
     */
    private List<Vec2> findCongestionPeers(
            ServerLevel level,
            Vec2 self,
            List<Vec2> candidatePoints
    ) {
        if (candidatePoints.isEmpty()) {
            return List.of();
        }
        double radius = SwarmCongestionPolicy.scanRadius(
                self, candidatePoints, SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.get()
        );
        return level.getEntitiesOfClass(
                        PathfinderMob.class,
                        mob.getBoundingBox().inflate(radius),
                        peer -> peer != mob
                                && peer.isAlive()
                                && SwarmAgentProfiles.isSupported(peer)
                ).stream()
                .map(peer -> new Vec2(peer.getX(), peer.getZ()))
                .toList();
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

    private SwarmNavigationRecoveryPolicy.Recovery chooseRecoveryWaypoint(
            ServerLevel level,
            Vec2 self,
            Vec2 destination
    ) {
        List<SwarmRecoveryCandidatePolicy.RecoveryCandidate> generated =
                SwarmRecoveryCandidatePolicy.generate(
                        self,
                        destination,
                        SwarmConfig.NAV_RECOVERY_LATERAL_DISTANCE.get()
                );

        if (generated.isEmpty()) {
            SwarmPathBudgetRegistry.cancel(level, mob);
            return new SwarmNavigationRecoveryPolicy.Recovery(destination, false);
        }

        List<Vec2> candidatePoints = generated.stream()
                .map(SwarmRecoveryCandidatePolicy.RecoveryCandidate::waypoint)
                .toList();
        boolean[] blockedCandidates = new boolean[generated.size()];
        for (int i = 0; i < candidatePoints.size(); i++) {
            Vec2 point = candidatePoints.get(i);
            blockedCandidates[i] = isProbeBlocked(level, point.x(), point.z());
        }
        int requiredPathQueries = SwarmPathProbePolicy.requiredQueries(
                blockedCandidates, SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get()
        );
        if (requiredPathQueries > 0
                && !SwarmPathBudgetRegistry.reserve(level, mob, requiredPathQueries)) {
            // null means deferred, not a recovery failure or evidence verdict.
            mob.getData(SwarmAttachments.AGENT_STATE.get()).clearPlannerTelemetry();
            return null;
        }
        List<Vec2> congestionPeers = SwarmPathProbePolicy.requiredQueries(blockedCandidates, true) == 0
                ? List.of()
                : findCongestionPeers(level, self, candidatePoints);
        double congestionRadius = SwarmConfig.NAV_LOCAL_CONGESTION_RADIUS.get();
        List<SwarmLocalPlannerPolicy.Candidate> candidates = new ArrayList<>();
        int blockedCount = 0;
        int unreachableCount = 0;
        int pathQueries = 0;
        for (int i = 0; i < generated.size(); i++) {
            var recoveryCandidate = generated.get(i);
            Vec2 candidate = candidatePoints.get(i);
            boolean blocked = blockedCandidates[i];
            if (blocked) blockedCount++;
            double congestion = SwarmCongestionPolicy.countWithin(congestionPeers, candidate, congestionRadius);

            boolean pathReachable = true;
            int pathNodeCount = 0;
            double pathResidualDistance = 0.0;

            if (!blocked && SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get()) {
                pathQueries++;
                var path = mob.getNavigation().createPath(
                        BlockPos.containing(candidate.x(), mob.getY(), candidate.z()),
                        0
                );
                if (path != null) {
                    pathNodeCount = path.getNodeCount();
                    pathResidualDistance = path.getDistToTarget();
                } else {
                    pathResidualDistance = Double.POSITIVE_INFINITY;
                }
                pathReachable = SwarmPathEvidencePolicy.acceptable(
                        path != null,
                        path != null && path.canReach(),
                        pathResidualDistance,
                        SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.get()
                );
                if (!pathReachable) unreachableCount++;
            }

            candidates.add(new SwarmLocalPlannerPolicy.Candidate(
                    candidate,
                    blocked,
                    recoveryCandidate.lateralOffset(),
                    congestion,
                    pathReachable,
                    pathNodeCount,
                    pathResidualDistance
            ));
        }

        SwarmLocalPlannerPolicy.Choice choice = SwarmLocalPlannerPolicy.choose(
                self,
                destination,
                candidates,
                SwarmConfig.NAV_LOCAL_PROGRESS_WEIGHT.get(),
                SwarmConfig.NAV_LOCAL_LATERAL_PENALTY.get(),
                SwarmConfig.NAV_LOCAL_CONGESTION_PENALTY.get(),
                SwarmConfig.NAV_PATH_NODE_PENALTY.get(),
                SwarmConfig.NAV_PATH_RESIDUAL_PENALTY.get()
        );

        mob.getData(SwarmAttachments.AGENT_STATE.get()).updatePlannerTelemetry(
                SwarmPlannerContext.RECOVERY,
                candidates.size(),
                blockedCount,
                unreachableCount,
                Math.max(0, candidates.size() - blockedCount - unreachableCount),
                choice.candidateIndex(),
                choice.score(),
                pathQueries
        );

        if (!choice.active()) {
            return new SwarmNavigationRecoveryPolicy.Recovery(destination, false);
        }

        return new SwarmNavigationRecoveryPolicy.Recovery(choice.waypoint(), true);
    }

    private void updateRecoveryState() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTick = level.getGameTime();
        SwarmAgentState state = mob.getData(SwarmAttachments.AGENT_STATE.get());
        if (SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                recoveryActive, state.directObservation(),
                state.behaviorMode(), recoveryPlanAnchorX, recoveryPlanAnchorZ,
                state.destinationX(), state.destinationZ())) {
            clearObsoleteNavigation(state);
            state.recordNavigationEpisodeReset();
            return;
        }
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
            var recovery = chooseRecoveryWaypoint(
                    level,
                    new Vec2(mob.getX(), mob.getZ()),
                    new Vec2(state.destinationX(), state.destinationZ())
            );
            if (recovery == null) {
                // Budget deferral is not an unreachable path or a failed plan.
                return;
            }
            state.recordRecoveryPlanning(recovery.active());

            if (recovery.active()) {
                obstacleDetourActive = false;
                obstacleDetourUntilTick = Long.MIN_VALUE;
                recoveryActive = true;
                recoveryPlanAnchorX = state.destinationX();
                recoveryPlanAnchorZ = state.destinationZ();
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
