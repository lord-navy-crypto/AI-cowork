package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmPlannerContext;
import dev.swarmmobs.algorithm.SwarmZombieEngineeringPolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Bounded emergency environment manipulation for swarm Zombies.
 *
 * This goal only activates on HARD difficulty after the normal local planner has
 * reported no feasible candidate. It may either:
 *  - hand-break one bounded obstacle directly on the current plan axis; or
 *  - place one salvaged block below the next step as a simple bridge support.
 *
 * It never replaces ordinary pathfinding when a normal route exists.
 */
public final class SwarmZombieEngineerGoal extends Goal {
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

    public SwarmZombieEngineerGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SwarmConfig.ENABLED.get()
                || !SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.get()
                || zombie.level().isClientSide()
                || zombie.level().getDifficulty() != Difficulty.HARD
                || !zombie.isAlive()) {
            return false;
        }

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        if (!state.hasDestination()
                || state.targetId() == null
                || state.plannerContext() == SwarmPlannerContext.NONE
                || state.plannerFeasibleCount() > 0) {
            return false;
        }

        // Never let engineering steal the close-range vanilla melee handoff.
        if (zombie.getTarget() != null
                && zombie.distanceToSqr(zombie.getTarget())
                <= SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get()
                * SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get()) {
            return false;
        }

        action = chooseAction(state);
        return action != Action.NONE;
    }

    @Override
    public boolean canContinueToUse() {
        return !completed
                && action != Action.NONE
                && actionPos != null
                && zombie.isAlive()
                && zombie.level().getDifficulty() == Difficulty.HARD;
    }

    @Override
    public void start() {
        completed = false;
        progressTicks = 0;
        zombie.getNavigation().stop();
    }

    @Override
    public void tick() {
        zombie.getNavigation().stop();

        if (action == Action.BREAK) {
            tickBreak();
        } else if (action == Action.BRIDGE) {
            tickBridge();
        }
    }

    @Override
    public void stop() {
        if (action == Action.BREAK && actionPos != null) {
            zombie.level().destroyBlockProgress(zombie.getId(), actionPos, -1);
        }
        action = Action.NONE;
        actionPos = null;
        sourceState = null;
        progressTicks = 0;
        requiredTicks = 0;
        completed = false;
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

        // Prefer a one-block bridge support when material is already available.
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
}
