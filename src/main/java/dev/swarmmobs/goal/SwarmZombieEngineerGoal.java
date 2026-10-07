package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmEngineeringTask;
import dev.swarmmobs.agent.SwarmPlannerContext;
import dev.swarmmobs.algorithm.SwarmBridgeSpanPolicy;
import dev.swarmmobs.algorithm.SwarmCombatBusyPolicy;
import dev.swarmmobs.algorithm.SwarmEngineeringEscalationPolicy;
import dev.swarmmobs.algorithm.SwarmEngineeringExecutionLeasePolicy;
import dev.swarmmobs.algorithm.SwarmEngineeringTaskPolicy;
import dev.swarmmobs.algorithm.SwarmPathEvidencePolicy;
import dev.swarmmobs.algorithm.SwarmZombieEngineeringPolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Bounded emergency environment manipulation for swarm Zombies.
 *
 * A blocked Zombie publishes a local engineering request. The requester selects
 * one deterministic claimant from nearby Zombies. Only that claimant may execute
 * the work, which suppresses duplicate breaking/bridging on the same local task.
 *
 * Shared tasks remain local and transient. They expire automatically and never
 * replace ordinary navigation or close-range vanilla melee.
 */
public final class SwarmZombieEngineerGoal extends Goal {
    private static final double WORK_RANGE = 2.25;

    private enum Action {
        NONE,
        BREAK,
        BRIDGE
    }

    private final Zombie zombie;
    private Action action = Action.NONE;
    private BlockPos actionPos;
    private BlockState sourceState;
    private int progressTicks;
    private int requiredTicks;
    private boolean completed;
    private boolean succeeded;
    private SwarmEngineeringTask activeTask;
    private long executionDeadlineTick = Long.MIN_VALUE;

    public SwarmZombieEngineerGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!baseEligible()) {
            return false;
        }

        ServerLevel level = (ServerLevel) zombie.level();
        long gameTick = level.getGameTime();
        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());

        SwarmEngineeringTask assigned = findAssignedTask(level, state, gameTick);
        if (assigned != null && configureFromTask(assigned, state)) {
            state.acceptEngineeringTask(assigned, gameTick);
            activeTask = assigned;
            armExecutionLease(gameTick);
            return true;
        }

        if (!requesterEligible(state)) {
            return false;
        }

        action = chooseAction(state);
        if (action == Action.NONE || actionPos == null) {
            return false;
        }

        SwarmEngineeringTask.Type type = action == Action.BRIDGE
                ? SwarmEngineeringTask.Type.BRIDGE
                : SwarmEngineeringTask.Type.BREAK;

        SwarmEngineeringTask existing = state.engineeringRequest(gameTick);
        SwarmEngineeringTask task;

        if (existing != null
                && taskMatchesCurrentTarget(existing, state)
                && existing.type() == type
                && existing.position().equals(actionPos)) {
            task = existing;
        } else {
            UUID claimant = chooseClaimant(level, state, type, actionPos);
            if (claimant == null) {
                return false;
            }

            task = state.publishEngineeringRequest(
                    type,
                    zombie.getUUID(),
                    claimant,
                    actionPos,
                    gameTick,
                    SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.get()
            );
        }

        if (!task.claimedBy(zombie.getUUID(), gameTick)) {
            return false;
        }

        state.acceptEngineeringTask(task, gameTick);
        activeTask = task;
        armExecutionLease(gameTick);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (completed
                || action == Action.NONE
                || actionPos == null
                || !SwarmConfig.ENABLED.get()
                || !SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.get()
                || !zombie.isAlive()
                || zombie.level().getDifficulty() != Difficulty.HARD
                || !zombie.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }

        if (activeTask != null && zombie.level() instanceof ServerLevel level) {
            SwarmAgentState state =
                    zombie.getData(SwarmAttachments.AGENT_STATE.get());
            return taskCompatibleWithExecutionTarget(activeTask, state)
                    && level.getGameTime() < executionDeadlineTick;
        }
        return true;
    }

    @Override
    public void start() {
        completed = false;
        succeeded = false;
        progressTicks = 0;
    }

    @Override
    public void tick() {
        if (!withinWorkRange()) {
            zombie.getNavigation().moveTo(
                    actionPos.getX() + 0.5,
                    actionPos.getY(),
                    actionPos.getZ() + 0.5,
                    SwarmConfig.MOVE_SPEED.get()
            );
            return;
        }

        zombie.getNavigation().stop();

        if (action == Action.BREAK) {
            tickBreak();
        } else if (action == Action.BRIDGE) {
            tickBridge();
        }

        if (completed) {
            if (succeeded) {
                completeCoordination();
            } else {
                cancelCoordination();
            }
        }
    }

    @Override
    public void stop() {
        if (action == Action.BREAK && actionPos != null) {
            zombie.level().destroyBlockProgress(zombie.getId(), actionPos, -1);
        }

        if (completed && succeeded) {
            completeCoordination();
        } else if (activeTask != null) {
            cancelCoordination();
        }

        action = Action.NONE;
        actionPos = null;
        sourceState = null;
        progressTicks = 0;
        requiredTicks = 0;
        completed = false;
        succeeded = false;
        activeTask = null;
        executionDeadlineTick = Long.MIN_VALUE;
    }

    private void armExecutionLease(long gameTick) {
        long advertisedExpiry = activeTask == null
                ? gameTick
                : activeTask.expiresTick();
        executionDeadlineTick = SwarmEngineeringExecutionLeasePolicy.deadline(
                gameTick,
                advertisedExpiry,
                requiredTicks
        );
    }

    private boolean baseEligible() {
        return SwarmConfig.ENABLED.get()
                && SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.get()
                && !zombie.level().isClientSide()
                && zombie.level().getDifficulty() == Difficulty.HARD
                && zombie.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && zombie.isAlive();
    }

    private boolean requesterEligible(SwarmAgentState state) {
        if (!state.hasDestination() || state.targetId() == null) {
            return false;
        }

        boolean engineeringPressure = SwarmEngineeringEscalationPolicy.shouldEscalate(
                state.plannerContext(),
                state.plannerBlockedCount(),
                state.plannerUnreachableCount(),
                state.plannerFeasibleCount()
        );

        return engineeringPressure && !meleeBusy(zombie);
    }

    private SwarmEngineeringTask findAssignedTask(
            ServerLevel level,
            SwarmAgentState state,
            long gameTick
    ) {
        SwarmEngineeringTask claimed = state.claimedEngineeringTask(gameTick);
        if (claimed != null) {
            if (claimed.claimedBy(zombie.getUUID(), gameTick)
                    && taskMatchesCurrentTarget(claimed, state)) {
                return claimed;
            }
            state.clearClaimedEngineeringTaskIfMatches(claimed);
        }

        UUID currentTarget = state.targetId();
        if (currentTarget == null) {
            return null;
        }

        double radius = SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get();
        return level.getEntitiesOfClass(
                        Zombie.class,
                        zombie.getBoundingBox().inflate(radius),
                        peer -> peer.isAlive() && SwarmAgentProfiles.isSupported(peer)
                ).stream()
                .filter(peer -> {
                    SwarmAgentState peerState =
                            peer.getData(SwarmAttachments.AGENT_STATE.get());
                    return currentTarget.equals(peerState.targetId());
                })
                .map(peer -> peer.getData(SwarmAttachments.AGENT_STATE.get()).engineeringRequest(gameTick))
                .filter(task -> task != null
                        && currentTarget.equals(task.targetId())
                        && task.claimedBy(zombie.getUUID(), gameTick))
                .min(Comparator.comparingDouble(this::distanceToTaskSqr))
                .orElse(null);
    }

    private static boolean taskMatchesCurrentTarget(
            SwarmEngineeringTask task,
            SwarmAgentState state
    ) {
        return task != null
                && state != null
                && state.targetId() != null
                && state.targetId().equals(task.targetId());
    }

    private static boolean taskCompatibleWithExecutionTarget(
            SwarmEngineeringTask task,
            SwarmAgentState state
    ) {
        if (task == null || state == null) {
            return false;
        }

        UUID currentTarget = state.targetId();
        return currentTarget == null || currentTarget.equals(task.targetId());
    }

    private UUID chooseClaimant(
            ServerLevel level,
            SwarmAgentState requesterState,
            SwarmEngineeringTask.Type type,
            BlockPos position
    ) {
        double radius = SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get();
        List<SwarmEngineeringTaskPolicy.Candidate> candidates = new ArrayList<>();

        List<Zombie> localZombies = new ArrayList<>(level.getEntitiesOfClass(
                Zombie.class,
                zombie.getBoundingBox().inflate(radius),
                peer -> peer.isAlive() && SwarmAgentProfiles.isSupported(peer)
        ));

        if (!localZombies.contains(zombie)) {
            localZombies.add(zombie);
        }

        localZombies.sort(Comparator.comparingDouble(
                candidate -> distanceToPositionSqr(candidate, position)
        ));

        int candidateLimit = Math.max(1, SwarmConfig.MAX_NEIGHBORS.get()) + 1;
        if (localZombies.size() > candidateLimit) {
            localZombies = new ArrayList<>(localZombies.subList(0, candidateLimit));
            if (!localZombies.contains(zombie)) {
                localZombies.set(localZombies.size() - 1, zombie);
            }
        }

        for (Zombie candidate : localZombies) {
            SwarmAgentState candidateState =
                    candidate.getData(SwarmAttachments.AGENT_STATE.get());

            if (candidate != zombie) {
                if (requesterState.targetId() == null
                        || candidateState.targetId() == null
                        || !requesterState.targetId().equals(candidateState.targetId())) {
                    continue;
                }
            }

            double distanceSqr = distanceToPositionSqr(candidate, position);
            boolean pathReachable = engineeringPathReachable(
                    candidate,
                    position,
                    distanceSqr
            );

            boolean materialAvailable = type != SwarmEngineeringTask.Type.BRIDGE
                    || candidateState.carriedEngineeringBlockCount() > 0
                    || findMaterialDonor(
                            candidate,
                            localZombies,
                            requesterState.targetId()
                    ) != null;

            candidates.add(new SwarmEngineeringTaskPolicy.Candidate(
                    candidate.getUUID(),
                    distanceSqr,
                    candidateState.role(),
                    candidateState.carriedEngineeringBlockCount(),
                    candidate == zombie,
                    meleeBusy(candidate),
                    pathReachable,
                    materialAvailable
            ));
        }

        UUID claimant = SwarmEngineeringTaskPolicy.chooseClaimant(type, candidates);
        if (claimant == null || type != SwarmEngineeringTask.Type.BRIDGE) {
            return claimant;
        }

        Zombie claimantZombie = localZombies.stream()
                .filter(candidate -> candidate.getUUID().equals(claimant))
                .findFirst()
                .orElse(null);
        if (claimantZombie == null) {
            return null;
        }

        SwarmAgentState claimantState =
                claimantZombie.getData(SwarmAttachments.AGENT_STATE.get());

        if (claimantState.carriedEngineeringBlockCount() > 0) {
            return claimant;
        }

        Zombie donor = findMaterialDonor(
                claimantZombie,
                localZombies,
                requesterState.targetId()
        );
        if (donor == null) {
            return null;
        }

        SwarmAgentState donorState =
                donor.getData(SwarmAttachments.AGENT_STATE.get());

        return donorState.transferOneEngineeringBlockTo(
                claimantState,
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
        )
                ? claimant
                : null;
    }

    private boolean engineeringPathReachable(
            Zombie candidate,
            BlockPos taskPosition,
            double distanceSqr
    ) {
        if (candidate == zombie || distanceSqr <= WORK_RANGE * WORK_RANGE) {
            return true;
        }

        if (!SwarmConfig.ZOMBIE_ENGINEERING_PATH_EVIDENCE_ENABLED.get()) {
            return true;
        }

        var path = candidate.getNavigation().createPath(taskPosition, 1);
        double residualDistance = path == null
                ? Double.POSITIVE_INFINITY
                : path.getDistToTarget();

        return SwarmPathEvidencePolicy.acceptable(
                path != null,
                path != null && path.canReach(),
                residualDistance,
                Math.max(
                        WORK_RANGE,
                        SwarmConfig.NAV_PATH_MAX_RESIDUAL_DISTANCE.get()
                )
        );
    }

    private static double distanceToPositionSqr(
            Zombie candidate,
            BlockPos position
    ) {
        double dx = candidate.getX() - (position.getX() + 0.5);
        double dy = candidate.getY() - (position.getY() + 0.5);
        double dz = candidate.getZ() - (position.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private Zombie findMaterialDonor(
            Zombie receiver,
            List<Zombie> localZombies,
            UUID targetId
    ) {
        if (receiver == null || localZombies == null || localZombies.isEmpty()) {
            return null;
        }

        double radius = SwarmConfig.ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS.get();
        double radiusSqr = radius * radius;
        SwarmAgentState receiverState =
                receiver.getData(SwarmAttachments.AGENT_STATE.get());

        return localZombies.stream()
                .filter(donor -> donor != receiver && donor.isAlive())
                .filter(donor -> {
                    SwarmAgentState donorState =
                            donor.getData(SwarmAttachments.AGENT_STATE.get());
                    return targetId != null
                            && targetId.equals(donorState.targetId())
                            && donorState.carriedEngineeringBlockCount() > 0
                            && donorState.carriedEngineeringBlock() != null
                            && !meleeBusy(donor)
                            && receiverState.canCarryEngineeringBlock(
                                    donorState.carriedEngineeringBlock(),
                                    SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
                            );
                })
                .filter(donor -> donor.distanceToSqr(receiver) <= radiusSqr)
                .min(
                        Comparator.comparingDouble(
                                        (Zombie donor) -> donor.distanceToSqr(receiver)
                                )
                                .thenComparing(donor -> donor.getUUID().toString())
                )
                .orElse(null);
    }

    private boolean configureFromTask(
            SwarmEngineeringTask task,
            SwarmAgentState state
    ) {
        if (task == null || task.position() == null) {
            return false;
        }

        actionPos = task.position();

        if (task.type() == SwarmEngineeringTask.Type.BRIDGE) {
            if (state.carriedEngineeringBlockCount() <= 0
                    || state.carriedEngineeringBlock() == null
                    || !zombie.level().getBlockState(actionPos).canBeReplaced()) {
                return false;
            }

            BlockState carried = state.carriedEngineeringBlock().getBlock().defaultBlockState();
            if (carried.getCollisionShape(zombie.level(), actionPos).isEmpty()
                    || !carried.canSurvive(zombie.level(), actionPos)) {
                return false;
            }

            sourceState = carried;
            requiredTicks = 6;
            action = Action.BRIDGE;
            return true;
        }

        BlockState block = zombie.level().getBlockState(actionPos);
        if (block.isAir()
                || block.getCollisionShape(zombie.level(), actionPos).isEmpty()) {
            return false;
        }

        double hardness = block.getDestroySpeed(zombie.level(), actionPos);
        boolean hasBlockEntity = zombie.level().getBlockEntity(actionPos) != null;
        if (!SwarmZombieEngineeringPolicy.canAttemptBreak(
                hardness,
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS.get(),
                hasBlockEntity
        )) {
            return false;
        }

        sourceState = block;
        requiredTicks = SwarmZombieEngineeringPolicy.handBreakTicks(
                hardness,
                block.requiresCorrectToolForDrops()
        );
        action = Action.BREAK;
        return true;
    }

    private Action chooseAction(SwarmAgentState state) {
        double dx = state.destinationX() - zombie.getX();
        double dz = state.destinationZ() - zombie.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) {
            return Action.NONE;
        }

        double stepX = dx / length;
        double stepZ = dz / length;
        BlockPos feetAhead = BlockPos.containing(
                zombie.getX() + stepX * 0.9,
                zombie.getY(),
                zombie.getZ() + stepZ * 0.9
        );

        BlockPos support = feetAhead.below();
        BlockState currentSupport = zombie.level().getBlockState(support);
        BlockState feetState = zombie.level().getBlockState(feetAhead);

        if (currentSupport.canBeReplaced()
                && feetState.getCollisionShape(zombie.level(), feetAhead).isEmpty()) {
            int gridStepX;
            int gridStepZ;
            if (Math.abs(stepX) >= Math.abs(stepZ)) {
                gridStepX = stepX >= 0.0 ? 1 : -1;
                gridStepZ = 0;
            } else {
                gridStepX = 0;
                gridStepZ = stepZ >= 0.0 ? 1 : -1;
            }

            BlockPos lowerSupport = support.below();
            boolean shallowSupport = !zombie.level()
                    .getBlockState(lowerSupport)
                    .getCollisionShape(zombie.level(), lowerSupport)
                    .isEmpty();

            if (shallowSupport) {
                if (availableBridgeMaterials(state) < 1) {
                    return Action.NONE;
                }
            } else {
                SwarmBridgeSpanPolicy.Evaluation bridge = evaluateBridgeSpan(
                        state,
                        support,
                        gridStepX,
                        gridStepZ
                );
                if (!bridge.allowed()) {
                    return Action.NONE;
                }
            }

            if (state.carriedEngineeringBlockCount() > 0
                    && state.carriedEngineeringBlock() != null) {
                BlockState carried = state.carriedEngineeringBlock()
                        .getBlock()
                        .defaultBlockState();
                if (!carried.getCollisionShape(zombie.level(), support).isEmpty()
                        && carried.canSurvive(zombie.level(), support)) {
                    sourceState = carried;
                }
            } else {
                sourceState = null;
            }

            actionPos = support;
            requiredTicks = 6;
            return Action.BRIDGE;
        }

        BlockPos[] candidates = {
                feetAhead,
                feetAhead.above()
        };

        for (BlockPos pos : candidates) {
            BlockState stateAtPos = zombie.level().getBlockState(pos);
            if (stateAtPos.isAir()
                    || stateAtPos.getCollisionShape(zombie.level(), pos).isEmpty()) {
                continue;
            }

            double hardness = stateAtPos.getDestroySpeed(zombie.level(), pos);
            boolean hasBlockEntity = zombie.level().getBlockEntity(pos) != null;
            if (!SwarmZombieEngineeringPolicy.canAttemptBreak(
                    hardness,
                    SwarmConfig.ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS.get(),
                    hasBlockEntity
            )) {
                continue;
            }

            actionPos = pos;
            sourceState = stateAtPos;
            requiredTicks = SwarmZombieEngineeringPolicy.handBreakTicks(
                    hardness,
                    stateAtPos.requiresCorrectToolForDrops()
            );
            return Action.BREAK;
        }

        return Action.NONE;
    }

    private SwarmBridgeSpanPolicy.Evaluation evaluateBridgeSpan(
            SwarmAgentState requesterState,
            BlockPos firstSupport,
            int stepX,
            int stepZ
    ) {
        int maxSpan = SwarmConfig.ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN.get();
        int gapLength = 0;
        boolean landingFound = false;

        for (int i = 0; i <= maxSpan; i++) {
            BlockPos support = firstSupport.offset(stepX * i, 0, stepZ * i);
            BlockPos feet = support.above();

            BlockState feetState = zombie.level().getBlockState(feet);
            if (!feetState.getCollisionShape(zombie.level(), feet).isEmpty()) {
                break;
            }

            BlockState supportState = zombie.level().getBlockState(support);
            if (supportState.canBeReplaced()) {
                if (i >= maxSpan) {
                    break;
                }
                gapLength++;
                continue;
            }

            if (!supportState.getCollisionShape(zombie.level(), support).isEmpty()) {
                landingFound = true;
            }
            break;
        }

        int availableMaterials = availableBridgeMaterials(requesterState);

        return SwarmBridgeSpanPolicy.evaluate(
                gapLength,
                landingFound,
                availableMaterials,
                maxSpan
        );
    }

    private int availableBridgeMaterials(SwarmAgentState requesterState) {
        if (!(zombie.level() instanceof ServerLevel level)
                || requesterState == null
                || requesterState.targetId() == null) {
            return 0;
        }

        double radius = SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get();
        List<Zombie> local = new ArrayList<>(level.getEntitiesOfClass(
                Zombie.class,
                zombie.getBoundingBox().inflate(radius),
                peer -> peer.isAlive() && SwarmAgentProfiles.isSupported(peer)
        ));

        if (!local.contains(zombie)) {
            local.add(zombie);
        }

        local.sort(Comparator.comparingDouble(zombie::distanceToSqr));
        int limit = Math.max(1, SwarmConfig.MAX_NEIGHBORS.get()) + 1;
        if (local.size() > limit) {
            local = new ArrayList<>(local.subList(0, limit));
            if (!local.contains(zombie)) {
                local.set(local.size() - 1, zombie);
            }
        }

        int total = 0;
        for (Zombie peer : local) {
            SwarmAgentState peerState =
                    peer.getData(SwarmAttachments.AGENT_STATE.get());

            if (!requesterState.targetId().equals(peerState.targetId())
                    || meleeBusy(peer)) {
                continue;
            }

            total += Math.max(0, peerState.carriedEngineeringBlockCount());
        }

        return total;
    }

    private boolean withinWorkRange() {
        if (actionPos == null) {
            return false;
        }

        double dx = zombie.getX() - (actionPos.getX() + 0.5);
        double dy = zombie.getY() - (actionPos.getY() + 0.5);
        double dz = zombie.getZ() - (actionPos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= WORK_RANGE * WORK_RANGE;
    }

    private double distanceToTaskSqr(SwarmEngineeringTask task) {
        if (task == null || task.position() == null) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = zombie.getX() - (task.position().getX() + 0.5);
        double dy = zombie.getY() - (task.position().getY() + 0.5);
        double dz = zombie.getZ() - (task.position().getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean meleeBusy(Zombie candidate) {
        return SwarmCombatBusyPolicy.isBusy(
                candidate.getTarget() != null,
                candidate.getTarget() != null && candidate.hasLineOfSight(candidate.getTarget()),
                candidate.getTarget() == null
                        ? Double.POSITIVE_INFINITY
                        : candidate.distanceToSqr(candidate.getTarget()),
                SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get()
        );
    }

    private void tickBreak() {
        if (actionPos == null || sourceState == null) {
            completed = true;
            succeeded = false;
            return;
        }

        BlockState current = zombie.level().getBlockState(actionPos);
        if (!current.is(sourceState.getBlock())) {
            completed = true;
            succeeded = false;
            return;
        }

        progressTicks++;
        int stage = Math.min(9, (int) Math.floor(10.0 * progressTicks / Math.max(1, requiredTicks)));
        zombie.level().destroyBlockProgress(zombie.getId(), actionPos, stage);

        if (progressTicks < requiredTicks) {
            return;
        }

        boolean hasBlockItem = sourceState.getBlock().asItem() instanceof BlockItem;
        boolean hasCollision = !sourceState.getCollisionShape(zombie.level(), actionPos).isEmpty();
        boolean salvage = SwarmZombieEngineeringPolicy.canSalvageAsBuildingMaterial(
                sourceState.requiresCorrectToolForDrops(),
                hasBlockItem,
                hasCollision
        );

        boolean destroyed = zombie.level().destroyBlock(actionPos, false, zombie);
        zombie.level().destroyBlockProgress(zombie.getId(), actionPos, -1);

        if (destroyed) {
            SwarmAgentState agentState =
                    zombie.getData(SwarmAttachments.AGENT_STATE.get());
            if (salvage && agentState.canCarryEngineeringBlock(
                    sourceState,
                    SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
            )) {
                agentState.salvageEngineeringBlock(
                        sourceState,
                        SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
                );
            }
        }

        completed = true;
        succeeded = destroyed;
    }

    private void tickBridge() {
        progressTicks++;
        if (progressTicks < requiredTicks) {
            return;
        }

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        if (actionPos == null
                || sourceState == null
                || state.carriedEngineeringBlockCount() <= 0
                || !zombie.level().getBlockState(actionPos).canBeReplaced()
                || !sourceState.canSurvive(zombie.level(), actionPos)) {
            completed = true;
            succeeded = false;
            return;
        }

        boolean placed = zombie.level().setBlockAndUpdate(actionPos, sourceState);
        if (placed) {
            state.consumeEngineeringBlock();
        }
        completed = true;
        succeeded = placed;
    }

    private void cancelCoordination() {
        if (activeTask == null) {
            return;
        }

        SwarmAgentState selfState = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        selfState.clearClaimedEngineeringTaskIfMatches(activeTask);

        if (activeTask.requesterId().equals(zombie.getUUID())) {
            selfState.clearEngineeringRequestIfMatches(activeTask);
            return;
        }

        if (zombie.level() instanceof ServerLevel level) {
            double radius = SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get() * 2.0;
            level.getEntitiesOfClass(
                            Zombie.class,
                            zombie.getBoundingBox().inflate(radius),
                            peer -> peer.isAlive()
                                    && peer.getUUID().equals(activeTask.requesterId())
                    ).stream()
                    .findFirst()
                    .ifPresent(requester ->
                            requester.getData(SwarmAttachments.AGENT_STATE.get())
                                    .clearEngineeringRequestIfMatches(activeTask)
                    );
        }
    }

    private void completeCoordination() {
        if (activeTask == null) {
            return;
        }

        SwarmAgentState selfState = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        selfState.completeEngineeringTask(activeTask);

        if (activeTask.requesterId().equals(zombie.getUUID())) {
            selfState.clearEngineeringRequestIfMatches(activeTask);
            return;
        }

        if (zombie.level() instanceof ServerLevel level) {
            double radius = SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get() * 2.0;
            level.getEntitiesOfClass(
                            Zombie.class,
                            zombie.getBoundingBox().inflate(radius),
                            peer -> peer.isAlive()
                                    && peer.getUUID().equals(activeTask.requesterId())
                    ).stream()
                    .findFirst()
                    .ifPresent(requester ->
                            requester.getData(SwarmAttachments.AGENT_STATE.get())
                                    .clearEngineeringRequestIfMatches(activeTask)
                    );
        }
    }
}
