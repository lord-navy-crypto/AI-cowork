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
import dev.swarmmobs.algorithm.SwarmCombatPlanner;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.algorithm.SwarmCommunicationPolicy;
import dev.swarmmobs.algorithm.SwarmFireSupportLanePolicy;
import dev.swarmmobs.algorithm.SwarmDivisionOfLaborPolicy;
import dev.swarmmobs.algorithm.SwarmEngineeringEscalationPolicy;
import dev.swarmmobs.algorithm.SwarmSpecializationRolePolicy;
import dev.swarmmobs.algorithm.SwarmTaskDemandPolicy;
import dev.swarmmobs.algorithm.SwarmTaskSaturationPolicy;
import dev.swarmmobs.algorithm.SwarmSearchPlanner;
import dev.swarmmobs.algorithm.SwarmSupportSpacingPolicy;
import dev.swarmmobs.algorithm.SwarmSensingPolicy;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.algorithm.TargetPredictionPolicy;
import dev.swarmmobs.algorithm.TargetRelayPolicy;
import dev.swarmmobs.ai.SwarmAiActiveState;
import dev.swarmmobs.ai.SwarmAiRoleBiasPolicy;
import dev.swarmmobs.ai.SwarmAiTaskDemandPolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.debug.SwarmDebugParticles;
import dev.swarmmobs.goal.SwarmApproachGoal;
import dev.swarmmobs.goal.SwarmCreeperSwellGoal;
import dev.swarmmobs.goal.SwarmSkeletonBowGoal;
import dev.swarmmobs.goal.SwarmZombieEngineerGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
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
            mob.goalSelector.addGoal(0, new SwarmZombieEngineerGoal(zombie));
        }

        mob.goalSelector.addGoal(1, new SwarmApproachGoal(mob));
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

        state.scheduleNextPlan(gameTick, interval);

        List<PathfinderMob> movementNeighbors = findMovementNeighbors(level, mob);
        List<PathfinderMob> communicationNeighbors = findCommunicationNeighbors(level, mob);

        receiveNeighborMessages(
                mob,
                communicationNeighbors,
                state,
                gameTick
        );

        TargetSelection selection = findTarget(level, mob, state, gameTick);

        if (selection == null) {
            state.recordSearchFailure();
            state.forgetTarget();
            state.clearLocalPlan(movementNeighbors.size());
            if (mob.getTarget() instanceof Player) {
                mob.setTarget(null);
            }
            return;
        }

        TargetObservation observation = selection.observation();
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

        List<CapabilitySlotAllocator.Member> capabilityMembers = movementNeighbors.stream()
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
                movementNeighbors.stream()
                        .map(peer -> SwarmAgentProfiles.profile(peer).archetype())
                        .toList()
        );

        SwarmAiActiveState.Snapshot activeStrategy =
                SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get()
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

            boolean combatBusy = mob.getTarget() != null
                    && mob.distanceToSqr(mob.getTarget())
                    <= SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get()
                    * SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get();

            EnumMap<SwarmTaskType, Integer> peerTaskOccupancy =
                    new EnumMap<>(SwarmTaskType.class);
            int sameTargetPeerCount = 0;
            for (PathfinderMob peer : movementNeighbors) {
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
        }

        SwarmRole tacticalRole = state.stabilizeRole(
                candidateRole,
                gameTick,
                SwarmConfig.ROLE_HYSTERESIS_TICKS.get()
        );

        if (searchMode) {
            state.beginSearchEpisode(gameTick, observation.targetId());
            state.clearPredictionTelemetry();

            int sameCapabilityCount = 1 + (int) movementNeighbors.stream()
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

            state.updateLocalPlan(
                    movementNeighbors.size(),
                    assignedSlot,
                    tacticalRole,
                    SwarmBehaviorMode.SEARCH,
                    searchPlan.searchRadius(),
                    searchPlan.destination().x(),
                    searchPlan.destination().z(),
                    searchPlan.separationMagnitude(),
                    searchPlan.cohesionMagnitude(),
                    searchPlan.alignmentMagnitude(),
                    searchPlan.steeringMagnitude()
            );
        } else {
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

            SwarmCombatPlanner.Plan plan = SwarmCombatPlanner.planForRoleWithMotion(
                    tacticalRole,
                    assignedSlot,
                    new Vec2(mob.getX(), mob.getZ()),
                    selfVelocity,
                    new Vec2(prediction.x(), prediction.z()),
                    new Vec2(observation.forwardX(), observation.forwardZ()),
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

            Vec2 plannedDestination = plan.destination();

            if (profile.archetype() == dev.swarmmobs.agent.SwarmAgentArchetype.RANGED_SUPPORT
                    && composition.hasBreacher()) {
                List<Vec2> breacherPositions = movementNeighbors.stream()
                        .filter(peer -> SwarmAgentProfiles.profile(peer).archetype()
                                == dev.swarmmobs.agent.SwarmAgentArchetype.BREACHER)
                        .map(peer -> new Vec2(peer.getX(), peer.getZ()))
                        .toList();

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

                double preferredSign = SwarmFireSupportLanePolicy.choosePreferredSign(
                        assignedSlot,
                        positiveClear,
                        negativeClear
                );

                plannedDestination = preferredSign > 0.0 ? positiveLane : negativeLane;
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

    private static List<PathfinderMob> findMovementNeighbors(
            ServerLevel level,
            PathfinderMob self
    ) {
        return findNearbyPeers(
                level,
                self,
                SwarmConfig.NEIGHBOR_RADIUS.get(),
                SwarmConfig.MAX_NEIGHBORS.get()
        );
    }

    private static List<PathfinderMob> findCommunicationNeighbors(
            ServerLevel level,
            PathfinderMob self
    ) {
        if (!SwarmConfig.COMMUNICATION_ENABLED.get()) {
            return List.of();
        }

        return findNearbyPeers(
                level,
                self,
                SwarmConfig.COMMUNICATION_RADIUS.get(),
                SwarmConfig.MAX_NEIGHBORS.get()
        );
    }

    private static List<PathfinderMob> findNearbyPeers(
            ServerLevel level,
            PathfinderMob self,
            double radius,
            int maxNeighbors
    ) {
        List<PathfinderMob> nearby = level.getEntitiesOfClass(
                PathfinderMob.class,
                self.getBoundingBox().inflate(radius),
                candidate -> candidate != self
                        && candidate.isAlive()
                        && !candidate.isNoAi()
                        && SwarmAgentProfiles.isSupported(candidate)
        );

        nearby.sort(Comparator.comparingDouble(self::distanceToSqr));
        int limit = Math.min(Math.max(0, maxNeighbors), nearby.size());
        return new ArrayList<>(nearby.subList(0, limit));
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

        Player direct = findDirectObservation(level, self);
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
            PathfinderMob self
    ) {
        double radius = SwarmConfig.TARGET_RADIUS.get();
        List<Player> players = level.getEntitiesOfClass(
                Player.class,
                self.getBoundingBox().inflate(radius),
                SwarmMobEvents::validTarget
        );

        return players.stream()
                .filter(self::hasLineOfSight)
                .min(Comparator.comparingDouble(self::distanceToSqr))
                .orElse(null);
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
