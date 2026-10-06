package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmEngineeringTask;
import dev.swarmmobs.agent.SwarmPlannerContext;
import dev.swarmmobs.algorithm.SwarmEngineeringTaskPolicy;
import dev.swarmmobs.algorithm.SwarmZombieEngineeringPolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.BlockItem;
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
    private SwarmEngineeringTask activeTask;

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
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (completed
                || action == Action.NONE
                || actionPos == null
                || !zombie.isAlive()
                || zombie.level().getDifficulty() != Difficulty.HARD) {
            return false;
        }

        if (activeTask != null && zombie.level() instanceof ServerLevel level) {
            return activeTask.active(level.getGameTime());
        }
        return true;
    }

    @Override
    public void start() {
        completed = false;
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
            completeCoordination();
        }
    }

    @Override
    public void stop() {
        if (action == Action.BREAK && actionPos != null) {
            zombie.level().destroyBlockProgress(zombie.getId(), actionPos, -1);
        }

        if (completed) {
            completeCoordination();
        }

        action = Action.NONE;
        actionPos = null;
        sourceState = null;
        progressTicks = 0;
        requiredTicks = 0;
        completed = false;
        activeTask = null;
    }

    private boolean baseEligible() {
        return SwarmConfig.ENABLED.get()
                && SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.get()
                && !zombie.level().isClientSide()
                && zombie.level().getDifficulty() == Difficulty.HARD
                && zombie.isAlive();
    }

    private boolean requesterEligible(SwarmAgentState state) {
        if (!state.hasDestination()
                || state.targetId() == null
                || state.plannerContext() == SwarmPlannerContext.NONE
                || state.plannerFeasibleCount() > 0) {
            return false;
        }

        return !meleeBusy(zombie);
    }

    private SwarmEngineeringTask findAssignedTask(
            ServerLevel level,
            SwarmAgentState state,
            long gameTick
    ) {
        SwarmEngineeringTask claimed = state.claimedEngineeringTask(gameTick);
        if (claimed != null && claimed.claimedBy(zombie.getUUID(), gameTick)) {
            return claimed;
        }

        double radius = SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get();
        return level.getEntitiesOfClass(
                        Zombie.class,
                        zombie.getBoundingBox().inflate(radius),
                        peer -> peer.isAlive() && SwarmAgentProfiles.isSupported(peer)
                ).stream()
                .map(peer -> peer.getData(SwarmAttachments.AGENT_STATE.get()).engineeringRequest(gameTick))
                .filter(task -> task != null && task.claimedBy(zombie.getUUID(), gameTick))
                .min(Comparator.comparingDouble(this::distanceToTaskSqr))
                .orElse(null);
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

            double dx = candidate.getX() - (position.getX() + 0.5);
            double dy = candidate.getY() - (position.getY() + 0.5);
            double dz = candidate.getZ() - (position.getZ() + 0.5);

            candidates.add(new SwarmEngineeringTaskPolicy.Candidate(
                    candidate.getUUID(),
                    dx * dx + dy * dy + dz * dz,
                    candidateState.role(),
                    candidateState.carriedEngineeringBlockCount(),
                    candidate == zombie,
                    meleeBusy(candidate)
            ));
        }

        return SwarmEngineeringTaskPolicy.chooseClaimant(type, candidates);
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

        if (state.carriedEngineeringBlockCount() > 0
                && state.carriedEngineeringBlock() != null) {
            BlockPos support = feetAhead.below();
            BlockState currentSupport = zombie.level().getBlockState(support);
            BlockState feetState = zombie.level().getBlockState(feetAhead);
            BlockState carried = state.carriedEngineeringBlock().getBlock().defaultBlockState();

            if (currentSupport.canBeReplaced()
                    && feetState.getCollisionShape(zombie.level(), feetAhead).isEmpty()
                    && !carried.getCollisionShape(zombie.level(), support).isEmpty()
                    && carried.canSurvive(zombie.level(), support)) {
                actionPos = support;
                sourceState = carried;
                requiredTicks = 6;
                return Action.BRIDGE;
            }
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
        if (candidate.getTarget() == null) {
            return false;
        }
        double release = SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get();
        return candidate.distanceToSqr(candidate.getTarget()) <= release * release;
    }

    private void tickBreak() {
        if (actionPos == null || sourceState == null) {
            completed = true;
            return;
        }

        BlockState current = zombie.level().getBlockState(actionPos);
        if (!current.is(sourceState.getBlock())) {
            completed = true;
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

        SwarmAgentState agentState = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        if (salvage && agentState.canCarryEngineeringBlock(
                sourceState,
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
        )) {
            agentState.salvageEngineeringBlock(
                    sourceState,
                    SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
            );
        }

        zombie.level().destroyBlock(actionPos, false, zombie);
        zombie.level().destroyBlockProgress(zombie.getId(), actionPos, -1);
        completed = true;
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
                || !zombie.level().getBlockState(actionPos).canBeReplaced()) {
            completed = true;
            return;
        }

        if (zombie.level().setBlockAndUpdate(actionPos, sourceState)) {
            state.consumeEngineeringBlock();
        }
        completed = true;
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
