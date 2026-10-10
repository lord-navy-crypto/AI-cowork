package dev.swarmmobs.event;

import dev.swarmmobs.agent.SwarmAgentProfile;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmLocalComposition;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.agent.SwarmSpecialization;
import dev.swarmmobs.agent.SwarmTaskType;
import dev.swarmmobs.algorithm.CapabilitySlotAllocator;
import dev.swarmmobs.algorithm.SwarmLocalRoleCoveragePolicy;
import dev.swarmmobs.algorithm.SwarmCombatPlanner;
import dev.swarmmobs.algorithm.SwarmAdaptiveTacticsPolicy;
import dev.swarmmobs.algorithm.SwarmPackDecongestionPolicy;
import dev.swarmmobs.algorithm.SwarmCrowdWaypointWorldPolicy;
import dev.swarmmobs.algorithm.SwarmCombatBusyPolicy;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.algorithm.SwarmCommunicationPolicy;
import dev.swarmmobs.algorithm.SwarmFireSupportLanePolicy;
import dev.swarmmobs.algorithm.SwarmFriendlyFireLanePolicy;
import dev.swarmmobs.algorithm.SwarmSupportPositionPolicy;
import dev.swarmmobs.algorithm.SwarmRangedSpacingPolicy;
import dev.swarmmobs.algorithm.SwarmSkeletonSightlinePolicy;
import dev.swarmmobs.algorithm.SwarmEngagementPolicy;
import dev.swarmmobs.algorithm.SwarmDivisionOfLaborPolicy;
import dev.swarmmobs.algorithm.SwarmEngineeringEscalationPolicy;
import dev.swarmmobs.algorithm.SwarmSpecializationRolePolicy;
import dev.swarmmobs.algorithm.SwarmTaskDemandPolicy;
import dev.swarmmobs.algorithm.SwarmTaskSaturationPolicy;
import dev.swarmmobs.algorithm.SwarmSearchPlanner;
import dev.swarmmobs.algorithm.SwarmSearchRallyPolicy;
import dev.swarmmobs.algorithm.SwarmNeighborSelectionPolicy;
import dev.swarmmobs.algorithm.SwarmSupportSpacingPolicy;
import dev.swarmmobs.algorithm.SwarmTargetSquadPolicy;
import dev.swarmmobs.algorithm.SwarmVisibleTargetPolicy;
import dev.swarmmobs.algorithm.SwarmSensingPolicy;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.algorithm.TargetPredictionPolicy;
import dev.swarmmobs.algorithm.TargetRelayPolicy;
import dev.swarmmobs.ai.SwarmAiActiveState;
import dev.swarmmobs.ai.SwarmAiControlPolicy;
import dev.swarmmobs.ai.SwarmAiRoleBiasPolicy;
import dev.swarmmobs.ai.SwarmAiTaskDemandPolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.debug.SwarmDebugParticles;
import dev.swarmmobs.goal.SwarmApproachGoal;
import dev.swarmmobs.goal.SwarmIdleNestGoal;
import dev.swarmmobs.goal.SwarmZombieColonyHaulGoal;
import dev.swarmmobs.goal.SwarmZombieBerryForageGoal;
import dev.swarmmobs.goal.SwarmZombieColonyGatherGoal;
import dev.swarmmobs.goal.SwarmZombiePheromoneExploreGoal;
import dev.swarmmobs.goal.SwarmZombieColonyHuntGoal;
import dev.swarmmobs.goal.SwarmSpiderColonyScoutGoal;
import dev.swarmmobs.goal.SwarmCreeperSwellGoal;
import dev.swarmmobs.goal.SwarmSkeletonBowGoal;
import dev.swarmmobs.goal.SwarmZombieEngineerGoal;
import dev.swarmmobs.goal.SwarmZombieBreacherSafetyGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public final class SwarmMobEvents {

    private record TargetSelection(
            TargetObservation observation,
            Player player,
            boolean direct
    ) {}

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof PathfinderMob mob)
                || event.getLevel().isClientSide()
                || !SwarmAgentProfiles.isSupported(mob)) {
            return;
        }

        if (mob instanceof Skeleton skeleton) {
            mob.goalSelector.addGoal(0, new SwarmSkeletonBowGoal(skeleton));
        } else if (mob instanceof Creeper creeper) {
            mob.goalSelector.addGoal(0, new SwarmCreeperSwellGoal(creeper));
        } else if (mob instanceof Zombie zombie) {
            // Active allied fuse is a rare, urgent MOVE handoff; otherwise
            // this never activates and existing engineering/melee remain intact.
            mob.goalSelector.addGoal(0, new SwarmZombieBreacherSafetyGoal(zombie));
            mob.goalSelector.addGoal(0, new SwarmZombieEngineerGoal(zombie));
        } else if (mob instanceof Spider spider) {
            // A sensor-only action with no MOVE/LOOK flags. Vanilla and
            // swarm tactical navigation retain control of the scout.
            mob.goalSelector.addGoal(2, new SwarmSpiderColonyScoutGoal(spider));
        }

        mob.goalSelector.addGoal(1, new SwarmApproachGoal(mob));
        if (mob instanceof Zombie builder) {
            mob.goalSelector.addGoal(2, new SwarmIdleNestGoal(builder));
            // Voluntary local-worker foraging yields to vanilla combat and
            // high-priority engineering, and is OFF until explicitly enabled.
            mob.goalSelector.addGoal(2, new SwarmZombieColonyHaulGoal(builder));
            // Renewable food harvest happens only when idle logistics has
            // no existing cargo to move; no attack or engineering override.
            mob.goalSelector.addGoal(3, new SwarmZombieColonyHuntGoal(builder));
            mob.goalSelector.addGoal(4, new SwarmZombieColonyGatherGoal(builder));
            mob.goalSelector.addGoal(5, new SwarmZombieBerryForageGoal(builder));
            // Last-resort short exploration along actual locally sensed
            // pheromone gradients, not an omniscient direct route to prey.
            mob.goalSelector.addGoal(6, new SwarmZombiePheromoneExploreGoal(builder));
        }
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!SwarmConfig.ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof PathfinderMob mob)
                || !SwarmAgentProfiles.isSupported(mob)) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel level) || mob.isNoAi() || !mob.isAlive()) {
            return;
        }

        SwarmAgentState state = mob.getData(SwarmAttachments.AGENT_STATE.get());
        long gameTick = level.getGameTime();
        int interval = SwarmConfig.PLAN_INTERVAL_TICKS.get();

        if (!state.planningScheduleInitialized()) {
            state.initializePlanSchedule(gameTick, interval, mob.getId());
        }

        if (gameTick < state.nextPlanTick()) {
            return;
        }

        // If the entire dimension currently has no players, a mob with no
        // target or queued relay has nothing to coordinate against. Avoid
        // running neighbor queries, line-of-sight checks and task allocation
        // for inactive swarms in loaded chunks. Poll once per second so
        // freshly entering players are detected without chunk tickets.
        if (level.players().isEmpty()
                && state.targetId() == null
                && state.pendingTargetMessageCount() == 0
                && mob.getTarget() == null) {
            state.scheduleNextPlan(gameTick, Math.max(interval, 20));
            state.updateEngagement(false,false,gameTick);
            state.clearLocalPlan(0);
            return;
        }

        state.scheduleNextPlan(gameTick, interval);

        var neighbors = findNearbyPeers(level, mob);
        List<PathfinderMob> movementNeighbors = neighbors.movement();
        List<PathfinderMob> communicationNeighbors = neighbors.communication();

        receiveNeighborMessages(
                mob,
                communicationNeighbors,
                state,
                gameTick
        );

        TargetSelection selection = findTarget(level, mob, state, gameTick);

        if (selection == null) {
            state.updateEngagement(false,false,gameTick);
            state.updateTacticalSquadTelemetry(0, 0);
            state.recordSearchFailure();
            state.forgetTarget();
            state.clearLocalPlan(movementNeighbors.size());
            if (mob.getTarget() instanceof Player) {
                mob.setTarget(null);
            }
            return;
        }

        TargetObservation observation = selection.observation();
        state.bindTacticalTarget(observation.targetId());

        // Tactical slots, task saturation and cross-species fire lanes belong
        // to allies currently pursuing the same target. Keep all physical
        // movement neighbors for separation/cohesion and all communication
        // neighbors for observation relay across the wider local swarm.
        List<PathfinderMob> tacticalNeighbors = SwarmTargetSquadPolicy.sameTarget(
                observation.targetId(),
                movementNeighbors,
                peer -> peer.getData(SwarmAttachments.AGENT_STATE.get()).targetId()
        );
        // Reuse the already acquired same-target neighbors. This is the
        // actual CURRENT Skeleton -> player game shot corridor, separate
        // from the two proposed support positions checked below.
        // RangedBowAttackGoal yields its MOVE handoff when a known allied
        // Zombie/Creeper stands directly in the firing lane.
        if (mob instanceof Skeleton && selection.direct()
                && selection.player() != null) {
            List<Vec2> blockingAllies = tacticalNeighbors.stream()
                    .filter(peer -> peer instanceof Zombie || peer instanceof Creeper)
                    .map(peer -> new Vec2(peer.getX(), peer.getZ()))
                    .toList();
            state.setBowLaneClear(SwarmFriendlyFireLanePolicy.isClear(
                    new Vec2(mob.getX(), mob.getZ()),
                    new Vec2(selection.player().getX(), selection.player().getZ()),
                    blockingAllies));
        } else {
            state.setBowLaneClear(true);
        }
        // A relayed sighting is ALERT, not permission to override combat.
        // A genuinely fighting same-target neighbor can locally recruit
        // a defender even before its own line-of-sight opens.
        boolean squadCombat = selection.direct() || tacticalNeighbors.stream()
                .anyMatch(peer -> {
                    SwarmAgentState ally = peer.getData(SwarmAttachments.AGENT_STATE.get());
                    long observedAt = ally.lastTargetObservationTick();
                    return ally.directObservation()
                            && observedAt >= 0
                            && gameTick >= observedAt
                            && gameTick-observedAt <= SwarmEngagementPolicy.DIRECT_GRACE_TICKS;
                });
        state.updateEngagement(true,squadCombat,gameTick);
        if (selection.direct()) {
            state.recordDirectReacquisition(gameTick, observation.targetId());
        }
        int slots = SwarmConfig.FORMATION_SLOTS.get();

        List<Vec2> neighborPositions = movementNeighbors.stream()
                .map(entity -> new Vec2(entity.getX(), entity.getZ()))
                .toList();

        List<Vec2> neighborVelocities = movementNeighbors.stream()
                .map(entity -> {
                    Vec3 velocity = entity.getDeltaMovement();
                    return new Vec2(velocity.x, velocity.z);
                })
                .toList();

        Vec3 selfVelocity3 = mob.getDeltaMovement();
        Vec2 selfVelocity = new Vec2(selfVelocity3.x, selfVelocity3.z);

        SwarmAgentProfile profile = SwarmAgentProfiles.profile(mob);

        List<CapabilitySlotAllocator.Member> capabilityMembers = tacticalNeighbors.stream()
                .map(peer -> new CapabilitySlotAllocator.Member(
                        peer.getUUID(),
                        SwarmAgentProfiles.profile(peer).archetype()
                ))
                .toList();

        int candidateSlot = CapabilitySlotAllocator.allocate(
                mob.getUUID(),
                profile.archetype(),
                capabilityMembers,
                slots
        );

        int assignedSlot = state.stabilizeFormationSlot(
                candidateSlot,
                gameTick,
                SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.get()
        );
        SwarmLocalComposition composition = SwarmLocalComposition.fromArchetypes(
                profile.archetype(),
                tacticalNeighbors.stream()
                        .map(peer -> SwarmAgentProfiles.profile(peer).archetype())
                        .toList()
        );
        state.updateTacticalSquadTelemetry(
                tacticalNeighbors.size(),
                (int) tacticalNeighbors.stream()
                        .filter(peer -> SwarmAgentProfiles.profile(peer).archetype()
                                == dev.swarmmobs.agent.SwarmAgentArchetype.BREACHER)
                        .count()
        );

        SwarmAiActiveState.Snapshot activeStrategy =
                SwarmAiControlPolicy.mayApplyActiveStrategy(
                        SwarmConfig.EXTERNAL_AI_ENABLED.get(),
                        SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get()
                )
                        ? SwarmAiActiveState.snapshot(gameTick)
                        : null;

        SwarmRole candidateRole = SwarmAgentProfiles.tacticalRole(
                profile.archetype(),
                assignedSlot,
                composition
        );

        if (activeStrategy != null && activeStrategy.active()) {
            candidateRole = SwarmAiRoleBiasPolicy.apply(
                    activeStrategy.decision().mode(),
                    profile.archetype(),
                    candidateRole,
                    assignedSlot,
                    composition
            );
        }

        double confidence = state.targetConfidence(
                gameTick,
                SwarmConfig.TARGET_MEMORY_TICKS.get()
        );

        // No artificial battle cadence. The optional local optimizer is
        // considered only for a directly-observing, same-target ranged
        // support agent with a real frontline. All other agents retain the
        // existing spatial swarm controller and native Minecraft Goals.
        final boolean optimizeSupport = SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.get()
                && SwarmEngagementPolicy.canCoordinateActiveSquad(
                        state.engagementMode(), tacticalNeighbors.size());
        if (!optimizeSupport) state.clearSupportPositionSide();

        double aiFormationMultiplier = activeStrategy == null
                ? 1.0
                : activeStrategy.formationRadiusMultiplier();
        double aiSeparationMultiplier = activeStrategy == null
                ? 1.0
                : activeStrategy.separationMultiplier();
        double aiCohesionMultiplier = activeStrategy == null
                ? 1.0
                : activeStrategy.cohesionMultiplier();
        double aiSearchRadiusMultiplier = activeStrategy == null
                ? 1.0
                : activeStrategy.searchRadiusMultiplier();

        boolean searchMode = !selection.direct()
                && confidence < SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.get();

        if (SwarmConfig.DIVISION_OF_LABOR_ENABLED.get()) {
            boolean routeBlocked = SwarmEngineeringEscalationPolicy.shouldEscalate(
                    state.plannerContext(),
                    state.plannerBlockedCount(),
                    state.plannerUnreachableCount(),
                    state.plannerFeasibleCount()
            );

            SwarmTaskDemandPolicy.Signals demandSignals =
                    new SwarmTaskDemandPolicy.Signals(
                            searchMode,
                            routeBlocked,
                            state.carriedEngineeringBlockCount() > 0,
                            composition.dedicatedFlankCoverage(),
                            composition.hasBreacher(),
                            composition.rangedSupportCount() > 0,
                            confidence
                    );

            boolean combatBusy = SwarmCombatBusyPolicy.isBusy(
                    mob.getTarget() != null,
                    mob.getTarget() != null && mob.hasLineOfSight(mob.getTarget()),
                    mob.getTarget() == null
                            ? Double.POSITIVE_INFINITY
                            : mob.distanceToSqr(mob.getTarget()),
                    SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get()
            );

            EnumMap<SwarmTaskType, Integer> peerTaskOccupancy =
                    new EnumMap<>(SwarmTaskType.class);
            int sameTargetPeerCount = 0;
            for (PathfinderMob peer : tacticalNeighbors) {
                SwarmAgentState peerState =
                        peer.getData(SwarmAttachments.AGENT_STATE.get());
                if (state.targetId() == null
                        || peerState.targetId() == null
                        || !state.targetId().equals(peerState.targetId())) {
                    continue;
                }
                sameTargetPeerCount++;
                peerTaskOccupancy.merge(peerState.currentTask(), 1, Integer::sum);
            }
            int localGroupSize = sameTargetPeerCount + 1;

            var assignment = SwarmDivisionOfLaborPolicy.choose(
                    mob.getUUID(),
                    profile.archetype(),
                    state.currentTask(),
                    task -> {
                        double demand = SwarmTaskDemandPolicy.demand(task, demandSignals);
                        if (activeStrategy != null && activeStrategy.active()) {
                            demand = SwarmAiTaskDemandPolicy.apply(
                                    demand,
                                    activeStrategy.decision().mode(),
                                    task
                            );
                        }
                        return SwarmTaskSaturationPolicy.adjustedDemand(
                                profile.archetype(),
                                task,
                                demand,
                                localGroupSize,
                                peerTaskOccupancy.getOrDefault(task, 0),
                                state.currentTask()
                        );
                    },
                    state::taskExperience,
                    0.0,
                    combatBusy
            );

            SwarmSpecialization specializationCandidate =
                    SwarmSpecialization.forAssignment(
                            profile.archetype(),
                            assignment.task(),
                            assignedSlot
                    );

            SwarmSpecialization specialization = state.stabilizeSpecialization(
                    assignment.task(),
                    specializationCandidate,
                    gameTick,
                    SwarmConfig.SPECIALIZATION_MIN_HOLD_TICKS.get()
            );

            state.updateTaskExperience(
                    state.currentTask(),
                    SwarmConfig.SPECIALIZATION_EXPERIENCE_GAIN.get(),
                    SwarmConfig.SPECIALIZATION_EXPERIENCE_DECAY.get()
            );

            candidateRole = SwarmSpecializationRolePolicy.role(
                    specialization,
                    candidateRole
            );
        } else {
            state.resetActiveSpecialization();
        }

        // Actual observed Spider coverage determines which flank is vacant.
        // Keep existing engineering/material workers and one assault front
        // anchor; deterministically offer spare same-target Zombies the
        // missing flank assignment without a centralized squad commander.
        var coverage = SwarmLocalRoleCoveragePolicy.choose(
                mob.getUUID(), profile.archetype(), state.currentTask(),
                candidateRole,
                tacticalNeighbors.stream().map(peer -> {
                    SwarmAgentState peerState = peer.getData(
                            SwarmAttachments.AGENT_STATE.get());
                    return new SwarmLocalRoleCoveragePolicy.Member(
                            peer.getUUID(), SwarmAgentProfiles.profile(peer).archetype(),
                            peerState.role(), peerState.currentTask());
                }).toList(),
                SwarmConfig.DIVISION_OF_LABOR_ENABLED.get()
                        && !searchMode && squadCombat && confidence >= 0.65);
        boolean urgentVacancy = coverage.fillingMissingFlank() || coverage.frontAnchor();
        SwarmRole tacticalRole = state.stabilizeRole(
                coverage.role(),
                gameTick,
                urgentVacancy
                        ? Math.min(8, SwarmConfig.ROLE_HYSTERESIS_TICKS.get())
                        : SwarmConfig.ROLE_HYSTERESIS_TICKS.get()
        );
        state.updateVacantFlankCoverage(
                coverage.fillingMissingFlank() && tacticalRole == coverage.role());

        if (searchMode) {
            state.updateRangedSpacing(false);
            state.setTacticalPattern("SEARCH");
            state.beginSearchEpisode(gameTick, observation.targetId());
            state.clearPredictionTelemetry();

            int sameCapabilityCount = 1 + (int) tacticalNeighbors.stream()
                    .filter(peer -> SwarmAgentProfiles.profile(peer).archetype() == profile.archetype())
                    .count();

            SwarmSearchPlanner.SearchPlan searchPlan = SwarmSearchPlanner.planWithMotion(
                    profile.archetype(),
                    assignedSlot,
                    new Vec2(mob.getX(), mob.getZ()),
                    selfVelocity,
                    new Vec2(observation.x(), observation.z()),
                    confidence,
                    Math.max(0L, gameTick - observation.observationTick()),
                    neighborPositions,
                    neighborVelocities,
                    sameCapabilityCount,
                    SwarmConfig.SEARCH_MIN_RADIUS.get() * aiSearchRadiusMultiplier,
                    SwarmConfig.SEARCH_MAX_RADIUS.get() * aiSearchRadiusMultiplier,
                    SwarmConfig.SEARCH_PHASE_TICKS.get(),
                    SwarmConfig.SEPARATION_RADIUS.get(),
                    SwarmConfig.SEPARATION_WEIGHT.get() * aiSeparationMultiplier,
                    SwarmConfig.COHESION_WEIGHT.get() * aiCohesionMultiplier,
                    SwarmConfig.ALIGNMENT_WEIGHT.get(),
                    SwarmConfig.MAX_STEERING_CORRECTION.get()
            );

            // Same-target local rejoining is a bounded modification of an
            // existing sector waypoint, not a new sighting of the player.
            // Uses already collected nearby peers: no global lookup.
            var rally = SwarmSearchRallyPolicy.adjust(
                    new Vec2(mob.getX(), mob.getZ()),
                    searchPlan.destination(),
                    new Vec2(observation.x(), observation.z()),
                    tacticalNeighbors.stream()
                            .map(peer -> new Vec2(peer.getX(), peer.getZ()))
                            .toList(),
                    SwarmConfig.DIVISION_OF_LABOR_ENABLED.get()
            );
            state.updateSearchRally(rally.regrouping());

            state.updateLocalPlan(
                    movementNeighbors.size(),
                    assignedSlot,
                    tacticalRole,
                    SwarmBehaviorMode.SEARCH,
                    searchPlan.searchRadius(),
                    rally.destination().x(),
                    rally.destination().z(),
                    searchPlan.separationMagnitude(),
                    searchPlan.cohesionMagnitude(),
                    searchPlan.alignmentMagnitude(),
                    searchPlan.steeringMagnitude()
            );
        } else {
            state.updateSearchRally(false);
            TargetPredictionPolicy.Prediction prediction;
            if (SwarmConfig.TARGET_PREDICTION_ENABLED.get()) {
                prediction = TargetPredictionPolicy.predict(
                        observation,
                        gameTick,
                        confidence,
                        SwarmConfig.TARGET_PREDICTION_LEAD_TICKS.get(),
                        SwarmConfig.TARGET_PREDICTION_MAX_TICKS.get(),
                        SwarmConfig.TARGET_PREDICTION_MAX_DISTANCE.get()
                );
            } else {
                prediction = new TargetPredictionPolicy.Prediction(
                        observation.x(),
                        observation.z(),
                        0.0,
                        0.0
                );
            }

            state.updatePredictionTelemetry(
                    prediction.x(),
                    prediction.z(),
                    prediction.offsetMagnitude()
            );

            double effectiveFormationRadius =
                    SwarmConfig.FORMATION_RADIUS.get()
                            * profile.formationRadiusMultiplier()
                            * aiFormationMultiplier;

            // Keep ranged support outside the breacher ingress lane. When a Creeper
            // is present locally, Skeletons widen their standoff instead of crowding
            // the same approach corridor.
            effectiveFormationRadius *= SwarmSupportSpacingPolicy.formationRadiusMultiplier(
                    profile.archetype(),
                    composition
            );
            if (SwarmConfig.DIVISION_OF_LABOR_ENABLED.get()) {
                effectiveFormationRadius *= SwarmSpecializationRolePolicy
                        .formationRadiusMultiplier(state.specialization());
            }

            // Distinct Minecraft squad geometries use only a recent, shared
            // target observation plus physical same-target peers, never a
            // hidden live player location or invented turn-based "round".
            var tacticalFrame = SwarmAdaptiveTacticsPolicy.choose(
                    observation, gameTick, confidence,
                    tacticalNeighbors.stream()
                            .map(peer -> new Vec2(peer.getX(),peer.getZ()))
                            .toList(),
                    SwarmConfig.DIVISION_OF_LABOR_ENABLED.get()
            );
            state.setTacticalPattern(tacticalFrame.pattern().name());

            SwarmCombatPlanner.Plan plan = SwarmCombatPlanner.planForRoleWithMotion(
                    tacticalRole,
                    assignedSlot,
                    new Vec2(mob.getX(), mob.getZ()),
                    selfVelocity,
                    new Vec2(prediction.x(), prediction.z()),
                    tacticalFrame.forward(),
                    neighborPositions,
                    neighborVelocities,
                    slots,
                    effectiveFormationRadius,
                    SwarmConfig.SEPARATION_RADIUS.get(),
                    SwarmConfig.SEPARATION_WEIGHT.get() * aiSeparationMultiplier,
                    SwarmConfig.COHESION_WEIGHT.get() * aiCohesionMultiplier,
                    SwarmConfig.ALIGNMENT_WEIGHT.get(),
                    SwarmConfig.MAX_STEERING_CORRECTION.get()
            );

            Vec2 plannedDestination = SwarmAdaptiveTacticsPolicy.refineDestination(
                    tacticalFrame, plan.destination(), tacticalRole, profile.archetype());
            plannedDestination = SwarmAdaptiveTacticsPolicy.farSideWaypoint(
                    tacticalFrame, plannedDestination,
                    new Vec2(prediction.x(), prediction.z()),
                    effectiveFormationRadius, tacticalRole, profile.archetype());

            // Crowded assault agents choose a less occupied local game lane
            // instead of all trying to occupy the same waypoint. No extra
            // scans: reuse the same-target peer positions already collected.
            var crowdChoice = SwarmPackDecongestionPolicy.choose(
                    new Vec2(mob.getX(), mob.getZ()),
                    plannedDestination, tacticalFrame.forward(),
                    tacticalNeighbors.stream()
                            .map(peer -> new Vec2(peer.getX(), peer.getZ()))
                            .toList(),
                    tacticalRole, profile.archetype(), assignedSlot,
                    state.crowdLaneSide(),
                    state.engagementMode() == SwarmEngagementPolicy.Mode.COMBAT
                            && selection.direct() && confidence >= 0.65);
            if (crowdChoice.active()) {
                // Cheap block-shape probes only in already-loaded chunks.
                // Do not force chunks or spend an extra pathfinding query.
                Vec2 right = SwarmPackDecongestionPolicy.waypoint(
                        plannedDestination, tacticalFrame.forward(), 1);
                Vec2 left = SwarmPackDecongestionPolicy.waypoint(
                        plannedDestination, tacticalFrame.forward(), -1);
                boolean rightUsable = SwarmCrowdWaypointWorldPolicy.locallyTraversable(level, mob.getY(), right);
                boolean leftUsable = SwarmCrowdWaypointWorldPolicy.locallyTraversable(level, mob.getY(), left);
                var feasible = SwarmPackDecongestionPolicy.chooseFeasible(
                        crowdChoice, rightUsable, leftUsable);
                boolean priorLaneBlocked = (state.crowdLaneSide() > 0 && !rightUsable)
                        || (state.crowdLaneSide() < 0 && !leftUsable);
                int crowdSide = state.acceptCrowdLane(
                        feasible.active() ? feasible.side() : 0,
                        gameTick, priorLaneBlocked);
                if (feasible.active() && crowdSide != 0) {
                    plannedDestination = SwarmPackDecongestionPolicy.waypoint(
                            plannedDestination, tacticalFrame.forward(), crowdSide);
                } else {
                    state.recordCrowdLaneRejected();
                }
            } else {
                state.acceptCrowdLane(0, gameTick);
            }

            if (profile.archetype() == dev.swarmmobs.agent.SwarmAgentArchetype.RANGED_SUPPORT) {
                // Only allies on the same active target count. Do not let
                // another independent squad distort this Skeleton's shots.
                List<PathfinderMob> frontline = tacticalNeighbors.stream()
                        .filter(peer -> peer instanceof Zombie || peer instanceof Creeper)
                        .toList();
                List<Vec2> breacherPositions = frontline.stream()
                        .filter(peer -> peer instanceof Creeper)
                        .map(peer -> new Vec2(peer.getX(), peer.getZ()))
                        .toList();
                List<Vec2> alliedPositions = frontline.stream()
                        .map(peer -> new Vec2(peer.getX(), peer.getZ()))
                        .toList();
                // When no Creeper is present, use the Zombie front as the
                // corridor axis, preserving a separate bow-fire lane.
                if (breacherPositions.isEmpty()) breacherPositions = alliedPositions;
                if (!breacherPositions.isEmpty()) {

                Vec2 targetPoint = new Vec2(prediction.x(), prediction.z());

                Vec2 positiveLane = SwarmFireSupportLanePolicy.applyWithPreferredSign(
                        plannedDestination,
                        targetPoint,
                        new Vec2(observation.forwardX(), observation.forwardZ()),
                        1.0,
                        breacherPositions
                );
                Vec2 negativeLane = SwarmFireSupportLanePolicy.applyWithPreferredSign(
                        plannedDestination,
                        targetPoint,
                        new Vec2(observation.forwardX(), observation.forwardZ()),
                        -1.0,
                        breacherPositions
                );

                boolean positiveClear = false;
                boolean negativeClear = false;
                if (selection.direct() && selection.player() != null) {
                    positiveClear = hasClearSupportShot(level, mob, selection.player(), positiveLane);
                    negativeClear = hasClearSupportShot(level, mob, selection.player(), negativeLane);
                }

                Vec2 targetPointForSafety = new Vec2(prediction.x(), prediction.z());
                boolean positiveFriendlyClear = SwarmFriendlyFireLanePolicy.isClear(
                        positiveLane,targetPointForSafety,alliedPositions);
                boolean negativeFriendlyClear = SwarmFriendlyFireLanePolicy.isClear(
                        negativeLane,targetPointForSafety,alliedPositions);
                if (optimizeSupport && selection.direct() && selection.player()!=null) {
                    // Real block visibility and same-target teammate line
                    // clearance are mandatory before considering either
                    // position. Compare feasible ones in consistent units.
                    var decision = SwarmSupportPositionPolicy.chooseSupport(
                            new Vec2(mob.getX(),mob.getZ()),
                            positiveLane,negativeLane,alliedPositions,
                            SwarmConfig.SEPARATION_RADIUS.get(),mob.getBbWidth(),
                            state.supportPositionSide(),assignedSlot,
                            positiveClear,negativeClear,
                            positiveFriendlyClear,negativeFriendlyClear);
                    state.acceptSupportPositionDecision(decision);
                    if (decision.side() > 0) plannedDestination = positiveLane;
                    else if (decision.side() < 0) plannedDestination = negativeLane;
                    // Neither feasible: preserve ordinary swarm planning.
                } else if (!SwarmConfig.SUPPORT_POSITION_OPTIMIZATION_ENABLED.get()) {
                    // Explicit OFF means the pre-optimizer legacy behavior.
                    double preferredSign = SwarmFriendlyFireLanePolicy.chooseSide(
                            assignedSlot,positiveClear,negativeClear,
                            positiveFriendlyClear,negativeFriendlyClear);
                    plannedDestination = preferredSign > 0.0
                            ? positiveLane : negativeLane;
                } else {
                    state.clearSupportPositionSide();
                    // Without a directly verified target there is no
                    // scientifically justified extra support-position move.
                }
                }
            }

            // Skeletons may move to a real playable square when (1) the
            // visible game player gets too close or (2) a known same-target
            // Zombie/Creeper blocks their line. Reuse the local squad
            // snapshot; no additional entity searches or hidden target reads.
            if (mob instanceof Skeleton skeleton) {
                boolean directVisible = selection.direct()
                        && selection.player() != null
                        && skeleton.hasLineOfSight(selection.player());
                Vec2 shooterPoint = new Vec2(mob.getX(), mob.getZ());
                Vec2 sightedPoint = new Vec2(observation.x(), observation.z());
                List<Vec2> frontPlayers = tacticalNeighbors.stream()
                        .filter(peer -> peer instanceof Zombie || peer instanceof Creeper)
                        .map(peer -> new Vec2(peer.getX(), peer.getZ()))
                        .toList();
                var spacing = SwarmRangedSpacingPolicy.consider(
                        shooterPoint, sightedPoint, directVisible, confidence);
                boolean attemptAllowed = state.mayAttemptRangedSpacing(gameTick);
                boolean possible = attemptAllowed && spacing.active()
                        && state.canUseRangedWaypoint(
                                spacing.candidate().x(), spacing.candidate().z(), gameTick)
                        && SwarmCrowdWaypointWorldPolicy.locallyTraversable(
                                level, mob.getY(), spacing.candidate())
                        && SwarmFriendlyFireLanePolicy.isClear(
                                spacing.candidate(), sightedPoint, frontPlayers)
                        && hasClearSupportShot(level, mob,
                                selection.player(), spacing.candidate());
                if (possible) {
                    plannedDestination = spacing.candidate();
                } else if (attemptAllowed && directVisible
                        // Do not preempt an unobstructed vanilla bow merely
                        // because the ideal retreat square does not exist.
                        // Sideways fallback is warranted only by an actual
                        // ally-blocked lane or recorded failed destination.
                        && (!state.bowLaneClear()
                                || (spacing.active()
                                        && state.hasRecentlyFailedRangedWaypoint(gameTick)))
                        && confidence >= 0.7) {
                    // A failed or obstructed backward step can select a
                    // different lateral square, rather than retrying the same
                    // unreachably planned location after each cooldown.
                    var pair = SwarmSkeletonSightlinePolicy.propose(
                            shooterPoint, sightedPoint);
                    boolean leftPossible = pair.valid()
                            && state.canUseRangedWaypoint(
                                    pair.left().x(), pair.left().z(), gameTick)
                            && SwarmCrowdWaypointWorldPolicy.locallyTraversable(
                                    level, mob.getY(), pair.left())
                            && SwarmFriendlyFireLanePolicy.isClear(
                                    pair.left(), sightedPoint, frontPlayers)
                            && hasClearSupportShot(level,mob,
                                    selection.player(), pair.left());
                    boolean rightPossible = pair.valid()
                            && state.canUseRangedWaypoint(
                                    pair.right().x(), pair.right().z(), gameTick)
                            && SwarmCrowdWaypointWorldPolicy.locallyTraversable(
                                    level, mob.getY(), pair.right())
                            && SwarmFriendlyFireLanePolicy.isClear(
                                    pair.right(), sightedPoint, frontPlayers)
                            && hasClearSupportShot(level,mob,
                                    selection.player(), pair.right());
                    Vec2 newLane = SwarmSkeletonSightlinePolicy.choose(
                            pair,leftPossible,rightPossible,assignedSlot);
                    if (newLane != null) {
                        possible = true;
                        plannedDestination = newLane;
                    }
                }
                // An expired waypoint lease may not instantly re-arm on
                // the next planner sample. Allow normal Skeleton bow combat
                // to resume before another optional sidestep.
                state.updateRangedSpacing(possible, gameTick);
            } else {
                state.updateRangedSpacing(false, gameTick);
            }

            state.updateLocalPlan(
                    movementNeighbors.size(),
                    plan.formationSlot(),
                    plan.role(),
                    SwarmBehaviorMode.ENGAGE,
                    0.0,
                    plannedDestination.x(),
                    plannedDestination.z(),
                    plan.separationMagnitude(),
                    plan.cohesionMagnitude(),
                    plan.alignmentMagnitude(),
                    plan.steeringMagnitude()
            );
        }

        if (selection.direct()) {
            mob.setTarget(selection.player());
        } else if (mob.getTarget() instanceof Player) {
            // An indirect observation may guide swarm positioning, but it must not
            // preserve an exact live Player reference through vanilla targeting.
            mob.setTarget(null);
        }

        if (gameTick % 10L == 0L) {
            SwarmDebugParticles.render(level, mob, communicationNeighbors);
        }
    }

    private static boolean hasClearSupportShot(
            ServerLevel level,
            PathfinderMob shooter,
            Player target,
            Vec2 candidate
    ) {
        Vec3 start = new Vec3(
                candidate.x(),
                shooter.getY() + shooter.getEyeHeight(),
                candidate.z()
        );
        Vec3 end = target.getEyePosition();

        HitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                shooter
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * One entity query feeds two independently capped channels. In particular a
     * wide communication radius must not crowd out the nearest movement peers.
     */
    private static SwarmNeighborSelectionPolicy.Selection<PathfinderMob> findNearbyPeers(
            ServerLevel level,
            PathfinderMob self
    ) {
        boolean communicationEnabled = SwarmConfig.COMMUNICATION_ENABLED.get();
        double movementRadius = SwarmConfig.NEIGHBOR_RADIUS.get();
        double communicationRadius = SwarmConfig.COMMUNICATION_RADIUS.get();
        double radius = communicationEnabled
                ? Math.max(movementRadius, communicationRadius)
                : movementRadius;
        double radiusSquared = radius * radius;

        List<PathfinderMob> candidates = level.getEntitiesOfClass(
                PathfinderMob.class,
                self.getBoundingBox().inflate(radius),
                candidate -> candidate != self
                        && candidate.isAlive()
                        && !candidate.isNoAi()
                        && SwarmAgentProfiles.isSupported(candidate)
                        && self.distanceToSqr(candidate) <= radiusSquared
        );

        return SwarmNeighborSelectionPolicy.select(
                candidates,
                self::distanceToSqr,
                movementRadius,
                communicationRadius,
                SwarmConfig.MAX_NEIGHBORS.get(),
                communicationEnabled
        );
    }

    private static void receiveNeighborMessages(
            PathfinderMob receiver,
            List<PathfinderMob> communicationNeighbors,
            SwarmAgentState receiverState,
            long gameTick
    ) {
        if (!SwarmConfig.COMMUNICATION_ENABLED.get()) {
            receiverState.clearPendingTargetMessages();
            return;
        }

        int memoryTicks = SwarmConfig.TARGET_MEMORY_TICKS.get();
        int latencyTicks = SwarmConfig.COMMUNICATION_LATENCY_TICKS.get();
        double dropRate = SwarmConfig.COMMUNICATION_PACKET_DROP_RATE.get();
        int experimentSeed = SwarmConfig.COMMUNICATION_EXPERIMENT_SEED.get();

        for (PathfinderMob sender : communicationNeighbors) {
            if (!SwarmCommunicationPolicy.withinRange(
                    receiver.distanceToSqr(sender),
                    SwarmConfig.COMMUNICATION_RADIUS.get()
            )) {
                continue;
            }

            SwarmAgentState senderState = sender.getData(SwarmAttachments.AGENT_STATE.get());
            TargetObservation observation = senderState.targetObservation();
            if (observation == null || !observation.hasFinitePosition()) {
                continue;
            }

            long age = gameTick - observation.observationTick();
            if (age < 0 || age > memoryTicks) {
                continue;
            }

            var message = SwarmCommunicationPolicy.maybeTransmit(
                    sender.getUUID(),
                    receiver.getUUID(),
                    observation,
                    gameTick,
                    latencyTicks,
                    dropRate,
                    experimentSeed
            );

            if (message.isPresent()) {
                receiverState.enqueueTargetMessage(message.get());
            } else if (dropRate > 0.0) {
                receiverState.recordCommunicationDrop();
            }
        }
    }

    private static TargetSelection findTarget(
            ServerLevel level,
            PathfinderMob self,
            SwarmAgentState state,
            long gameTick
    ) {
        int memoryTicks = SwarmConfig.TARGET_MEMORY_TICKS.get();
        List<TargetObservation> records = new ArrayList<>();

        for (var message : state.drainDeliverableTargetMessages(gameTick)) {
            records.add(message.observation());
        }

        Player direct = findDirectObservation(
                level, self, state.directObservation() ? state.targetId() : null
        );
        if (direct != null) {
            Vec3 look = direct.getLookAngle();
            Vec3 velocity = direct.getDeltaMovement();

            SwarmSensingPolicy.Sample sensingSample;
            if (SwarmConfig.SENSING_IMPERFECTION_ENABLED.get()) {
                sensingSample = SwarmSensingPolicy.samplePosition(
                        self.getUUID(),
                        direct.getUUID(),
                        gameTick,
                        direct.getX(),
                        direct.getY(),
                        direct.getZ(),
                        SwarmConfig.SENSING_DROPOUT_RATE.get(),
                        SwarmConfig.SENSING_MAX_HORIZONTAL_NOISE.get(),
                        SwarmConfig.SENSING_EXPERIMENT_SEED.get()
                );
            } else {
                sensingSample = new SwarmSensingPolicy.Sample(
                        true,
                        direct.getX(),
                        direct.getY(),
                        direct.getZ(),
                        0.0
                );
            }

            if (sensingSample.observed()) {
                TargetObservation observation = new TargetObservation(
                        direct.getUUID(),
                        gameTick,
                        sensingSample.measuredX(),
                        sensingSample.measuredY(),
                        sensingSample.measuredZ(),
                        look.x,
                        look.z,
                        velocity.x,
                        velocity.z
                );
                state.recordSensingAccepted(sensingSample.horizontalNoiseMagnitude());
                state.rememberTarget(observation, true);
                return new TargetSelection(observation, direct, true);
            }

            state.recordSensingDrop();
        }

        // Vanilla targeting may already know about a player behind an obstacle.
        // Treat that as an indirect observation instead of requiring the swarm
        // layer to have seen the player before the wall existed. Without this
        // bridge, a Zombie facing a pre-existing base wall can have a valid
        // vanilla attack target while engineering remains permanently blind
        // because swarm targetId never becomes initialized.
        if (state.targetObservation() == null
                && self.getTarget() instanceof Player vanillaTarget
                && validTarget(vanillaTarget)
                && !self.hasLineOfSight(vanillaTarget)
                && self.distanceToSqr(vanillaTarget)
                        <= SwarmConfig.TARGET_RADIUS.get() * SwarmConfig.TARGET_RADIUS.get()) {
            Vec3 look = vanillaTarget.getLookAngle();
            Vec3 velocity = vanillaTarget.getDeltaMovement();
            TargetObservation observation = new TargetObservation(
                    vanillaTarget.getUUID(),
                    gameTick,
                    vanillaTarget.getX(),
                    vanillaTarget.getY(),
                    vanillaTarget.getZ(),
                    look.x,
                    look.z,
                    velocity.x,
                    velocity.z
            );
            state.rememberTarget(observation, false);
            return new TargetSelection(observation, vanillaTarget, false);
        }

        if (state.targetObservation() != null) {
            records.add(state.targetObservation());
        }

        var selected = TargetRelayPolicy.selectFreshest(gameTick, memoryTicks, records);
        if (selected.isPresent()) {
            TargetObservation observation = selected.get();
            Player shared = resolvePlayer(level, observation.targetId());
            if (shared != null && observation.hasFinitePosition()) {
                state.rememberTarget(observation, false);
                return new TargetSelection(observation, shared, false);
            }
        }

        return null;
    }

    private static Player findDirectObservation(
            ServerLevel level,
            PathfinderMob self,
            UUID incumbentId
    ) {
        double radius = SwarmConfig.TARGET_RADIUS.get();
        double radiusSqr = radius * radius;
        List<Player> players = level.getEntitiesOfClass(
                Player.class,
                self.getBoundingBox().inflate(radius),
                player -> validTarget(player)
                        && self.distanceToSqr(player) <= radiusSqr
        );

        // Only candidates with current real line of sight are eligible. A
        // relayed or occluded old player must never receive sticky priority.
        return SwarmVisibleTargetPolicy.choose(
                players.stream().filter(self::hasLineOfSight).toList(),
                incumbentId,
                Player::getUUID,
                self::distanceToSqr
        );
    }

    private static Player resolvePlayer(ServerLevel level, UUID id) {
        var entity = level.getEntity(id);
        if (entity instanceof Player player && validTarget(player)) {
            return player;
        }
        return null;
    }

    private static boolean validTarget(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private SwarmMobEvents() {}
}
