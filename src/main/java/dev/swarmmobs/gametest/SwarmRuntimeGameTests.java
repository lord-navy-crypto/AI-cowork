package dev.swarmmobs.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import dev.swarmmobs.SwarmMobs;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.agent.SwarmEngineeringTask;
import dev.swarmmobs.agent.SwarmNavigationMode;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.agent.SwarmPlannerContext;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.algorithm.SwarmPathBudgetRegistry;
import dev.swarmmobs.algorithm.SwarmNavigationCommandTelemetry;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.goal.SwarmApproachGoal;
import dev.swarmmobs.goal.SwarmIdleNestGoal;
import dev.swarmmobs.goal.SwarmZombieColonyHaulGoal;
import dev.swarmmobs.goal.SwarmZombieBerryForageGoal;
import dev.swarmmobs.goal.SwarmZombieColonyGatherGoal;
import dev.swarmmobs.goal.SwarmZombieColonyHuntGoal;
import dev.swarmmobs.goal.SwarmSpiderColonyScoutGoal;
import dev.swarmmobs.goal.SwarmZombiePheromoneExploreGoal;
import dev.swarmmobs.colony.SwarmNestPheromoneField;
import dev.swarmmobs.colony.SwarmNestOpportunityBoard;
import dev.swarmmobs.colony.SwarmNestHaulLease;
import dev.swarmmobs.colony.SwarmNestScoutSignal;
import net.minecraft.world.entity.item.ItemEntity;
import dev.swarmmobs.registry.SwarmNestBlocks;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.goal.SwarmCreeperSwellGoal;
import dev.swarmmobs.goal.SwarmZombieEngineerGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class SwarmRuntimeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private SwarmRuntimeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_integration", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void zombieReceivesSwarmRuntimeIntegration(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setNoGravity(true);

        helper.runAfterDelay(4, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            if (state == null) {
                helper.fail("Zombie did not expose SwarmAgentState attachment");
                return;
            }

            boolean hasApproachGoal = zombie.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.getGoal() instanceof SwarmApproachGoal);
            if (!hasApproachGoal) {
                helper.fail("SwarmApproachGoal was not installed on zombie join");
                return;
            }

            boolean hasEngineerGoal = zombie.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.getGoal() instanceof SwarmZombieEngineerGoal);
            if (!hasEngineerGoal) {
                helper.fail("SwarmZombieEngineerGoal was not installed on zombie join");
                return;
            }

            state.updateLocalPlan(2, 1, SwarmRole.FLANK_LEFT, 4.0, 4.0, 0.25, 0.50);
            if (!state.hasDestination()
                    || state.role() != SwarmRole.FLANK_LEFT
                    || state.neighborCount() != 2) {
                helper.fail("SwarmAgentState did not retain runtime planning telemetry");
                return;
            }

            helper.succeed();
        });
    }
    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_vanilla_target_bridge", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void vanillaPlayerTargetBehindWallSeedsIndirectSwarmTarget(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);

        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        zombie.setTarget(player);

        helper.runAfterDelay(18, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());

            if (zombie.hasLineOfSight(player)) {
                playerHandle.close();
                helper.fail("Wall fixture unexpectedly left direct line of sight");
                return;
            }
            if (!player.getUUID().equals(state.targetId())) {
                playerHandle.close();
                helper.fail("Vanilla target behind wall did not seed swarm target memory");
                return;
            }
            if (state.directObservation()) {
                playerHandle.close();
                helper.fail("Occluded vanilla target was incorrectly marked direct");
                return;
            }
            if (!state.hasDestination()) {
                playerHandle.close();
                helper.fail("Indirect vanilla target did not produce a swarm destination");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_sensing_dropout_vanilla_guard", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void directSensingDropoutIsNotBypassedByVanillaTarget(GameTestHelper helper) {
        boolean previousImperfect = SwarmConfig.SENSING_IMPERFECTION_ENABLED.get();
        double previousDropout = SwarmConfig.SENSING_DROPOUT_RATE.get();

        SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(true);
        SwarmConfig.SENSING_DROPOUT_RATE.set(1.0);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);
        zombie.setTarget(player);

        helper.runAfterDelay(18, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());

            if (!zombie.hasLineOfSight(player)) {
                SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(previousImperfect);
                SwarmConfig.SENSING_DROPOUT_RATE.set(previousDropout);
                playerHandle.close();
                helper.fail("Dropout guard fixture unexpectedly lost direct LOS");
                return;
            }
            if (state.targetId() != null || state.hasDestination()) {
                SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(previousImperfect);
                SwarmConfig.SENSING_DROPOUT_RATE.set(previousDropout);
                playerHandle.close();
                helper.fail("Vanilla target bypassed a forced direct sensing dropout");
                return;
            }

            SwarmConfig.SENSING_IMPERFECTION_ENABLED.set(previousImperfect);
            SwarmConfig.SENSING_DROPOUT_RATE.set(previousDropout);
            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void zombieBreaksSoftObstacleAndReusesItAsBridgeSupport(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not expose SwarmZombieEngineerGoal");
            return;
        }

        if (!engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie engineering did not activate for a blocked HARD-mode route");
            return;
        }

        engineer.start();
        for (int i = 0; i < 20; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not hand-break the soft obstacle");
            return;
        }
        if (state.engineeringBlocksBroken() != 1L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie engineering break telemetry did not increment");
            return;
        }
        if (state.carriedEngineeringBlockCount() != 1) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not salvage the hand-harvestable obstacle as building material");
            return;
        }

        zombie.setPos(zombie.getX(), zombie.getY() + 1.0, zombie.getZ());
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos bridgeSupport = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY() - 1.0,
                zombie.getZ()
        );
        helper.getLevel().removeBlock(bridgeSupport, false);
        helper.getLevel().setBlockAndUpdate(
                bridgeSupport.east(),
                Blocks.STONE.defaultBlockState()
        );
        helper.getLevel().removeBlock(bridgeSupport.east().above(), false);

        if (!engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie engineering did not select bridge support with carried material");
            return;
        }

        engineer.start();
        for (int i = 0; i < 8; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (helper.getLevel().getBlockState(bridgeSupport).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not place salvaged material as bridge support");
            return;
        }
        if (state.engineeringBlocksPlaced() != 1L || state.carriedEngineeringBlockCount() != 0) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie bridge placement telemetry/inventory did not update");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_real_escalation", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void obstacleDetourWithDirectBreakableBlockEscalatesWithoutSyntheticPlannerFailure(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );

        // This deliberately does NOT inject blocked/unreachable/feasible planner
        // counts. It reproduces the live failure mode where ordinary navigation
        // has already entered detour handling but still reports nominal lateral
        // options, which previously prevented engineering forever.
        state.updateNavigationTelemetry(
                SwarmNavigationMode.OBSTACLE_DETOUR,
                zombie.getX() + 1.5,
                zombie.getZ(),
                false
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Direct breakable obstacle did not escalate from live detour state");
            return;
        }

        engineer.start();
        for (int i = 0; i < 20; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Escalated engineering did not break the direct DIRT obstacle");
            return;
        }
        if (state.engineeringBlocksBroken() != 1L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Real-escalation break telemetry did not increment");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_negative_break", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void changedBreakTargetCancelsInsteadOfReportingSuccess(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Negative break fixture could not start Zombie engineering");
            return;
        }

        engineer.start();

        // Simulate the world/player changing the selected block after the task was claimed.
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.STONE.defaultBlockState());
        engineer.tick();
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).is(Blocks.STONE)) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Changed break target was mutated by stale engineering work");
            return;
        }
        if (state.engineeringTasksCompleted() != 0L
                || state.engineeringBlocksBroken() != 0L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Changed break target was incorrectly reported as completed");
            return;
        }
        if (state.claimedEngineeringTask(helper.getTick()) != null
                || state.engineeringRequest(helper.getTick()) != null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Failed break left stale engineering coordination");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_state_change", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void sameBlockStateMutationCancelsStaleBreak(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        var closed = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH);
        var opened = closed.setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH);
        helper.getLevel().setBlockAndUpdate(obstacle, closed);

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("State-change break fixture could not start engineering");
            return;
        }

        engineer.start();

        // Same Block type, different BlockState: stale progress must not carry over.
        helper.getLevel().setBlockAndUpdate(obstacle, opened);
        engineer.tick();
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).equals(opened)) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Changed stair state was mutated by stale break work");
            return;
        }
        if (state.engineeringBlocksBroken() != 0L
                || state.engineeringTasksCompleted() != 0L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("State-mutated obstacle was incorrectly reported as completed");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_negative_bridge", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void occupiedBridgeSiteCancelsWithoutConsumingMaterial(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);
        zombie.setPos(zombie.getX(), zombie.getY() + 1.0, zombie.getZ());

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.salvageEngineeringBlock(
                Blocks.DIRT.defaultBlockState(),
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
        );
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos bridgeSupport = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY() - 1.0,
                zombie.getZ()
        );
        helper.getLevel().removeBlock(bridgeSupport, false);
        helper.getLevel().setBlockAndUpdate(
                bridgeSupport.east(),
                Blocks.STONE.defaultBlockState()
        );
        helper.getLevel().removeBlock(bridgeSupport.east().above(), false);

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Negative bridge fixture could not start Zombie engineering");
            return;
        }

        engineer.start();

        // Simulate another actor occupying the reserved bridge support before placement.
        helper.getLevel().setBlockAndUpdate(bridgeSupport, Blocks.STONE.defaultBlockState());
        for (int i = 0; i < 8; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (!helper.getLevel().getBlockState(bridgeSupport).is(Blocks.STONE)) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Occupied bridge site was overwritten by stale engineering work");
            return;
        }
        if (state.engineeringTasksCompleted() != 0L
                || state.engineeringBlocksPlaced() != 0L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Blocked bridge placement was incorrectly reported as completed");
            return;
        }
        if (state.carriedEngineeringBlockCount() != 1) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Failed bridge placement consumed engineering material");
            return;
        }
        if (state.claimedEngineeringTask(helper.getTick()) != null
                || state.engineeringRequest(helper.getTick()) != null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Failed bridge placement left stale engineering coordination");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_radius", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void diagonalAabbPeerOutsideTrueRadiusCannotClaimEngineeringTask(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        double previousRadius = SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.get();

        server.setDifficulty(Difficulty.HARD, true);
        SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(2.0);

        Zombie requester = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie diagonalHelper = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        requester.setNoGravity(true);
        diagonalHelper.setNoGravity(true);

        if (requester.distanceTo(diagonalHelper) <= 2.0) {
            SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(previousRadius);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Radius fixture did not place helper outside true 2-block radius");
            return;
        }

        UUID sharedTarget = UUID.randomUUID();
        SwarmAgentState requesterState = requester.getData(SwarmAttachments.AGENT_STATE.get());
        SwarmAgentState helperState = diagonalHelper.getData(SwarmAttachments.AGENT_STATE.get());

        requesterState.rememberTarget(sharedTarget, helper.getTick(), false);
        helperState.rememberTarget(sharedTarget, helper.getTick(), false);

        BlockPos obstacle = BlockPos.containing(
                diagonalHelper.getX() + 0.9,
                diagonalHelper.getY(),
                diagonalHelper.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        requesterState.publishEngineeringRequest(
                SwarmEngineeringTask.Type.BREAK,
                requester.getUUID(),
                diagonalHelper.getUUID(),
                obstacle,
                helper.getTick(),
                40
        );

        SwarmZombieEngineerGoal helperGoal = diagonalHelper.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (helperGoal == null) {
            SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(previousRadius);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Diagonal helper did not expose engineering goal");
            return;
        }

        if (helperGoal.canUse()) {
            SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(previousRadius);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("AABB-corner Zombie incorrectly claimed task outside true engineering radius");
            return;
        }

        if (helperState.claimedEngineeringTask(helper.getTick()) != null) {
            SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(previousRadius);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Out-of-radius Zombie retained an engineering claim");
            return;
        }

        SwarmConfig.ZOMBIE_ENGINEERING_TASK_RADIUS.set(previousRadius);
        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_execution_lease", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 160)
    public static void claimedLongBreakOutlivesEngineeringAdvertisementTtl(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        player.setNoGravity(true);
        player.setPos(zombie.getX() + 4.0, zombie.getY(), zombie.getZ());

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(
                new TargetObservation(
                        player.getUUID(),
                        helper.getTick(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        1.0,
                        0.0
                ),
                false
        );
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                player.getX(),
                player.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.STONE.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null) {
            playerHandle.close();
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Long-break engineering fixture did not expose engineer goal");
            return;
        }

        // Isolate execution-lease behavior from Minecraft GoalSelector
        // lifecycle management before canUse configures mutable action state.
        zombie.goalSelector.removeGoal(engineer);

        if (!engineer.canUse()) {
            playerHandle.close();
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Long-break engineering fixture could not configure engineer goal");
            return;
        }

        engineer.start();

        int afterAdvertisementExpiry =
                SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.get() + 5;

        helper.runAfterDelay(afterAdvertisementExpiry, () -> {
            // Hold target identity constant: this fixture tests execution lease
            // vs advertisement TTL, not perception-memory refresh behavior.
            state.rememberTarget(
                    new TargetObservation(
                            player.getUUID(),
                            helper.getTick(),
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            1.0,
                            0.0
                    ),
                    false
            );

            if (!engineer.canContinueToUse()) {
                playerHandle.close();
                server.setDifficulty(previousDifficulty, true);
                helper.fail("Claimed long break was aborted when advertisement TTL expired");
                return;
            }

            for (int i = 0; i < 170; i++) {
                engineer.tick();
            }
            engineer.stop();

            if (!helper.getLevel().getBlockState(obstacle).isAir()) {
                playerHandle.close();
                server.setDifficulty(previousDifficulty, true);
                helper.fail("Long-break engineering lease did not allow STONE removal");
                return;
            }
            if (state.engineeringBlocksBroken() != 1L) {
                playerHandle.close();
                server.setDifficulty(previousDifficulty, true);
                helper.fail("Confirmed STONE removal was missing from engineering break telemetry");
                return;
            }
            if (state.carriedEngineeringBlockCount() != 0) {
                playerHandle.close();
                server.setDifficulty(previousDifficulty, true);
                helper.fail("Tool-required STONE was incorrectly salvaged as carried material");
                return;
            }

            playerHandle.close();
            server.setDifficulty(previousDifficulty, true);
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_falling_material", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void fallingBlockObstacleIsBrokenButNotSalvagedAsBridgeMaterial(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.SAND.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Falling-material fixture could not start Zombie engineering");
            return;
        }

        engineer.start();
        for (int i = 0; i < 24; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie failed to remove breakable SAND obstacle");
            return;
        }
        if (state.engineeringBlocksBroken() != 1L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("SAND removal was missing from engineering break telemetry");
            return;
        }
        if (state.carriedEngineeringBlockCount() != 0) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Falling SAND was incorrectly salvaged as bridge material");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_unstable_shape_material", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void nonFullSupportObstacleIsBrokenButNotSalvagedAsBridgeMaterial(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(
                obstacle,
                Blocks.OAK_FENCE.defaultBlockState()
        );

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Non-full-support material fixture could not start Zombie engineering");
            return;
        }

        engineer.start();
        for (int i = 0; i < 90; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie failed to remove breakable OAK_FENCE obstacle");
            return;
        }
        if (state.engineeringBlocksBroken() != 1L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("OAK_FENCE removal was missing from engineering break telemetry");
            return;
        }
        if (state.carriedEngineeringBlockCount() != 0) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("OAK_FENCE was incorrectly accepted as stable bridge material");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_invalid_inventory", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void invalidCarriedBlockDoesNotSatisfyBridgePreflight(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);
        zombie.setPos(zombie.getX(), zombie.getY() + 1.0, zombie.getZ());

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.salvageEngineeringBlock(
                Blocks.OAK_DOOR.defaultBlockState(),
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
        );
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos bridgeSupport = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY() - 1.0,
                zombie.getZ()
        );
        helper.getLevel().removeBlock(bridgeSupport, false);
        helper.getLevel().removeBlock(bridgeSupport.below(), false);
        helper.getLevel().setBlockAndUpdate(
                bridgeSupport.east(),
                Blocks.STONE.defaultBlockState()
        );
        helper.getLevel().removeBlock(bridgeSupport.above(), false);
        helper.getLevel().removeBlock(bridgeSupport.east().above(), false);

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Invalid-inventory fixture did not expose Zombie engineering goal");
            return;
        }

        if (engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Invalid carried OAK_DOOR incorrectly satisfied bridge preflight");
            return;
        }

        if (state.engineeringRequest(helper.getTick()) != null
                || state.claimedEngineeringTask(helper.getTick()) != null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Rejected bridge material still created stale engineering coordination");
            return;
        }

        if (state.carriedEngineeringBlockCount() != 1) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Rejected carried material was consumed during preflight");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_target_unknown", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void committedEngineeringSurvivesTemporaryTargetUncertainty(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Target-uncertainty fixture could not start Zombie engineering");
            return;
        }

        engineer.start();
        state.forgetTarget();

        if (!engineer.canContinueToUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Temporary target uncertainty incorrectly aborted committed engineering");
            return;
        }

        for (int i = 0; i < 24; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Committed engineering failed to finish during bounded target uncertainty");
            return;
        }
        if (state.engineeringTasksCompleted() != 1L
                || state.claimedEngineeringTask(helper.getTick()) != null
                || state.engineeringRequest(helper.getTick()) != null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Target-uncertainty completion left incorrect engineering coordination");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_recovery", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void recoveryCanEscalateToZombieEngineeringWithOneFallbackCandidate(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.RECOVERY,
                6,
                3,
                2,
                1,
                5,
                0.25,
                6L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not expose SwarmZombieEngineerGoal");
            return;
        }

        if (!engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("RECOVERY did not escalate to engineering while one fallback candidate remained");
            return;
        }

        engineer.start();
        for (int i = 0; i < 20; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Recovery-escalated Zombie engineer did not break the blocking soft obstacle");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_close_wall", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void closePlayerBehindWallDoesNotSuppressZombieEngineering(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        player.setNoGravity(true);
        player.setPos(zombie.getX() + 2.5, zombie.getY(), zombie.getZ());

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(obstacle.above(), Blocks.DIRT.defaultBlockState());

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(player.getUUID(), helper.getTick(), true);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                player.getX(),
                player.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.RECOVERY,
                6,
                4,
                1,
                1,
                5,
                0.25,
                6L
        );
        zombie.setTarget(player);

        if (zombie.distanceToSqr(player)
                > SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get()
                * SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get()) {
            playerHandle.close();
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Fixture did not place player inside nominal melee handoff distance");
            return;
        }

        if (zombie.hasLineOfSight(player)) {
            playerHandle.close();
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Fixture wall did not block Zombie line of sight");
            return;
        }

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            playerHandle.close();
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Close blocked player incorrectly suppressed Zombie engineering");
            return;
        }

        engineer.start();
        for (int i = 0; i < 20; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (!helper.getLevel().getBlockState(obstacle).isAir()) {
            playerHandle.close();
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not break the soft wall blocking a close player");
            return;
        }

        playerHandle.close();
        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_disable", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void activeZombieEngineeringStopsWhenControlIsDisabled(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        boolean previousMaster = SwarmConfig.ENABLED.get();
        boolean previousEngineering = SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.get();

        server.setDifficulty(Difficulty.HARD, true);
        SwarmConfig.ENABLED.set(true);
        SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(previousEngineering);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Fixture could not start Zombie engineering before disable test");
            return;
        }

        engineer.start();

        SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(false);
        if (engineer.canContinueToUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(previousEngineering);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Active Zombie engineering ignored Engineering OFF");
            return;
        }

        engineer.stop();
        if (state.claimedEngineeringTask(helper.getTick()) != null
                || state.engineeringRequest(helper.getTick()) != null) {
            SwarmConfig.ENABLED.set(previousMaster);
            SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(previousEngineering);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Aborted Zombie engineering left stale claim/request coordination");
            return;
        }

        SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(true);
        if (!engineer.canUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(previousEngineering);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie engineering could not restart cleanly after stale coordination was released");
            return;
        }
        engineer.start();

        SwarmConfig.ENABLED.set(false);
        if (engineer.canContinueToUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(previousEngineering);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Active Zombie engineering ignored Swarm master OFF");
            return;
        }

        engineer.stop();
        if (state.claimedEngineeringTask(helper.getTick()) != null
                || state.engineeringRequest(helper.getTick()) != null) {
            SwarmConfig.ENABLED.set(previousMaster);
            SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(previousEngineering);
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Swarm master OFF left stale engineering coordination");
            return;
        }

        SwarmConfig.ENABLED.set(previousMaster);
        SwarmConfig.ZOMBIE_ENGINEERING_ENABLED.set(previousEngineering);
        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_target_binding", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void engineeringAbortsWhenZombieSwitchesTarget(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        UUID originalTarget = UUID.randomUUID();
        UUID replacementTarget = UUID.randomUUID();

        state.rememberTarget(originalTarget, helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Target-binding fixture could not start Zombie engineering");
            return;
        }

        var request = state.engineeringRequest(helper.getTick());
        if (request == null || !originalTarget.equals(request.targetId())) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Engineering request was not bound to the original target");
            return;
        }

        engineer.start();
        state.rememberTarget(replacementTarget, helper.getTick() + 1, false);

        if (engineer.canContinueToUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie kept executing stale engineering after target switch");
            return;
        }

        engineer.stop();

        if (state.claimedEngineeringTask(helper.getTick()) != null
                || state.engineeringRequest(helper.getTick()) != null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Target switch left stale engineering coordination");
            return;
        }

        if (!helper.getLevel().getBlockState(obstacle).is(Blocks.DIRT)) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie mutated the old route after switching targets");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_engineering_gamerule", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void zombieEngineeringRespectsMobGriefing(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        boolean previousMobGriefing = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);

        server.setDifficulty(Difficulty.HARD, true);
        helper.getLevel().getGameRules()
                .getRule(GameRules.RULE_MOBGRIEFING)
                .set(false, server);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null) {
            restoreEngineeringGameRules(server, helper, previousDifficulty, previousMobGriefing);
            helper.fail("Zombie did not expose SwarmZombieEngineerGoal");
            return;
        }

        if (engineer.canUse()) {
            restoreEngineeringGameRules(server, helper, previousDifficulty, previousMobGriefing);
            helper.fail("Zombie engineering ignored mobGriefing=false");
            return;
        }

        if (!helper.getLevel().getBlockState(obstacle).is(Blocks.DIRT)) {
            restoreEngineeringGameRules(server, helper, previousDifficulty, previousMobGriefing);
            helper.fail("Zombie engineering mutated terrain while mobGriefing was disabled");
            return;
        }

        restoreEngineeringGameRules(server, helper, previousDifficulty, previousMobGriefing);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_shared_zombie_engineering", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void blockedZombieDelegatesEngineeringToSingleLocalHelper(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie requester = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Zombie helperZombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 3));
        requester.setNoGravity(true);
        helperZombie.setNoGravity(true);

        UUID sharedTarget = UUID.randomUUID();
        SwarmAgentState requesterState = requester.getData(SwarmAttachments.AGENT_STATE.get());
        SwarmAgentState helperState = helperZombie.getData(SwarmAttachments.AGENT_STATE.get());

        requesterState.rememberTarget(sharedTarget, helper.getTick(), false);
        helperState.rememberTarget(sharedTarget, helper.getTick(), false);

        requesterState.updateLocalPlan(
                1,
                0,
                SwarmRole.CHASER,
                requester.getX() + 4.0,
                requester.getZ(),
                0.0,
                0.0
        );
        requesterState.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        helperState.updateLocalPlan(
                1,
                3,
                SwarmRole.REAR_PRESSURE,
                helperZombie.getX() + 4.0,
                helperZombie.getZ(),
                0.0,
                0.0
        );

        BlockPos obstacle = BlockPos.containing(
                requester.getX() + 0.9,
                requester.getY(),
                requester.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());

        SwarmZombieEngineerGoal requesterGoal = requester.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);
        SwarmZombieEngineerGoal helperGoal = helperZombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (requesterGoal == null || helperGoal == null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Shared engineering test Zombies did not expose engineer goals");
            return;
        }

        if (requesterGoal.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Blocked requester incorrectly kept task instead of delegating to better helper");
            return;
        }

        var request = requesterState.engineeringRequest(helper.getLevel().getGameTime());
        if (request == null || !helperZombie.getUUID().equals(request.claimantId())) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Engineering request did not claim the selected helper Zombie");
            return;
        }

        if (!helperGoal.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Claimed helper did not accept neighbor engineering request");
            return;
        }

        helperGoal.start();
        for (int i = 0; i < 20; i++) {
            helperGoal.tick();
        }
        helperGoal.stop();

        if (!helper.getLevel().getBlockState(obstacle).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Claimed helper did not complete shared break task");
            return;
        }

        if (helperState.engineeringBlocksBroken() != 1L
                || helperState.engineeringTasksClaimed() != 1L
                || helperState.engineeringTasksCompleted() != 1L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Shared engineering claimant telemetry did not record helper completion");
            return;
        }

        if (requesterState.engineeringRequest(helper.getLevel().getGameTime()) != null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Completed shared engineering request was not cleared");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_requester_material_handoff", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void requesterReconfiguresBridgeAfterReceivingDonorMaterial(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie requester = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Zombie donor = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 3));
        requester.setNoGravity(true);
        donor.setNoGravity(true);
        requester.setPos(requester.getX(), requester.getY() + 1.0, requester.getZ());
        donor.setPos(donor.getX(), donor.getY() + 1.0, donor.getZ());

        UUID sharedTarget = UUID.randomUUID();
        SwarmAgentState requesterState = requester.getData(SwarmAttachments.AGENT_STATE.get());
        SwarmAgentState donorState = donor.getData(SwarmAttachments.AGENT_STATE.get());

        requesterState.rememberTarget(sharedTarget, helper.getTick(), false);
        donorState.rememberTarget(sharedTarget, helper.getTick(), false);

        requesterState.updateLocalPlan(
                1,
                0,
                SwarmRole.REAR_PRESSURE,
                requester.getX() + 4.0,
                requester.getZ(),
                0.0,
                0.0
        );
        requesterState.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );
        donorState.updateLocalPlan(
                1,
                0,
                SwarmRole.CHASER,
                donor.getX() + 4.0,
                donor.getZ(),
                0.0,
                0.0
        );
        donorState.salvageEngineeringBlock(
                Blocks.DIRT.defaultBlockState(),
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
        );

        BlockPos bridgeSupport = BlockPos.containing(
                requester.getX() + 0.9,
                requester.getY() - 1.0,
                requester.getZ()
        );
        helper.getLevel().removeBlock(bridgeSupport, false);
        helper.getLevel().removeBlock(bridgeSupport.below(), false);
        helper.getLevel().setBlockAndUpdate(
                bridgeSupport.east(),
                Blocks.STONE.defaultBlockState()
        );
        helper.getLevel().removeBlock(bridgeSupport.above(), false);
        helper.getLevel().removeBlock(bridgeSupport.east().above(), false);

        SwarmZombieEngineerGoal requesterGoal = requester.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (requesterGoal == null || !requesterGoal.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Requester could not claim bridge after donor material handoff");
            return;
        }

        var request = requesterState.engineeringRequest(helper.getTick());
        if (request == null || !requester.getUUID().equals(request.claimantId())) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Requester-handoff fixture did not keep bridge claim on requester");
            return;
        }

        if (donorState.carriedEngineeringBlockCount() != 0
                || requesterState.carriedEngineeringBlockCount() != 1
                || donorState.engineeringMaterialsGiven() != 1L
                || requesterState.engineeringMaterialsReceived() != 1L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Requester did not receive exactly one donor bridge material");
            return;
        }

        requesterGoal.start();
        for (int i = 0; i < 8; i++) {
            requesterGoal.tick();
        }
        requesterGoal.stop();

        if (helper.getLevel().getBlockState(bridgeSupport).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Requester kept stale pre-handoff bridge state and failed placement");
            return;
        }
        if (requesterState.engineeringBlocksPlaced() != 1L
                || requesterState.carriedEngineeringBlockCount() != 0) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Requester bridge placement did not consume transferred material");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_material_handoff", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void bridgeClaimantReceivesOneBlockFromNearbyDonor(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie requester = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Zombie claimant = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 3));
        Zombie donor = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 4));
        requester.setNoGravity(true);
        claimant.setNoGravity(true);
        donor.setNoGravity(true);

        requester.setPos(requester.getX(), requester.getY() + 1.0, requester.getZ());
        claimant.setPos(claimant.getX(), claimant.getY() + 1.0, claimant.getZ());
        donor.setPos(donor.getX(), donor.getY() + 1.0, donor.getZ());

        UUID sharedTarget = UUID.randomUUID();
        SwarmAgentState requesterState = requester.getData(SwarmAttachments.AGENT_STATE.get());
        SwarmAgentState claimantState = claimant.getData(SwarmAttachments.AGENT_STATE.get());
        SwarmAgentState donorState = donor.getData(SwarmAttachments.AGENT_STATE.get());

        requesterState.rememberTarget(sharedTarget, helper.getTick(), false);
        claimantState.rememberTarget(sharedTarget, helper.getTick(), false);
        donorState.rememberTarget(sharedTarget, helper.getTick(), false);

        requesterState.updateLocalPlan(
                2,
                0,
                SwarmRole.CHASER,
                requester.getX() + 4.0,
                requester.getZ(),
                0.0,
                0.0
        );
        requesterState.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        claimantState.updateLocalPlan(
                2,
                3,
                SwarmRole.REAR_PRESSURE,
                claimant.getX() + 4.0,
                claimant.getZ(),
                0.0,
                0.0
        );
        donorState.updateLocalPlan(
                2,
                0,
                SwarmRole.CHASER,
                donor.getX() + 4.0,
                donor.getZ(),
                0.0,
                0.0
        );

        donorState.salvageEngineeringBlock(
                Blocks.DIRT.defaultBlockState(),
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
        );

        BlockPos bridgeSupport = BlockPos.containing(
                requester.getX() + 0.9,
                requester.getY() - 1.0,
                requester.getZ()
        );
        helper.getLevel().removeBlock(bridgeSupport, false);
        helper.getLevel().setBlockAndUpdate(
                bridgeSupport.east(),
                Blocks.STONE.defaultBlockState()
        );
        helper.getLevel().removeBlock(bridgeSupport.east().above(), false);

        SwarmZombieEngineerGoal requesterGoal = requester.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);
        SwarmZombieEngineerGoal claimantGoal = claimant.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (requesterGoal == null || claimantGoal == null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Material-handoff test Zombies did not expose engineer goals");
            return;
        }

        if (requesterGoal.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Bridge requester incorrectly kept task instead of delegating");
            return;
        }

        var request = requesterState.engineeringRequest(helper.getLevel().getGameTime());
        if (request == null || !claimant.getUUID().equals(request.claimantId())) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Bridge request did not select expected local claimant");
            return;
        }

        if (donorState.carriedEngineeringBlockCount() != 0
                || claimantState.carriedEngineeringBlockCount() != 1
                || donorState.engineeringMaterialsGiven() != 1L
                || claimantState.engineeringMaterialsReceived() != 1L) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("One-block engineering material handoff did not occur exactly once");
            return;
        }

        if (!claimantGoal.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Claimant did not accept bridge task after receiving material");
            return;
        }

        claimantGoal.start();
        for (int i = 0; i < 8; i++) {
            claimantGoal.tick();
        }
        claimantGoal.stop();

        if (helper.getLevel().getBlockState(bridgeSupport).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Claimant did not place transferred material as bridge support");
            return;
        }

        if (claimantState.engineeringBlocksPlaced() != 1L
                || claimantState.carriedEngineeringBlockCount() != 0
                || requesterState.engineeringRequest(helper.getLevel().getGameTime()) != null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Bridge completion did not consume material and clear shared task");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_bounded_bridge_span", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void zombiePreflightsTwoBlockBridgeBeforeCommitting(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);
        zombie.setPos(zombie.getX(), zombie.getY() + 1.0, zombie.getZ());

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        UUID targetId = UUID.randomUUID();
        state.rememberTarget(targetId, helper.getTick(), false);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 5.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        BlockPos firstSupport = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY() - 1.0,
                zombie.getZ()
        );
        BlockPos secondSupport = firstSupport.east();
        BlockPos landingSupport = secondSupport.east();

        helper.getLevel().removeBlock(firstSupport, false);
        helper.getLevel().removeBlock(secondSupport, false);
        helper.getLevel().removeBlock(firstSupport.below(), false);
        helper.getLevel().removeBlock(secondSupport.below(), false);
        helper.getLevel().setBlockAndUpdate(
                landingSupport,
                Blocks.STONE.defaultBlockState()
        );
        helper.getLevel().removeBlock(firstSupport.above(), false);
        helper.getLevel().removeBlock(secondSupport.above(), false);
        helper.getLevel().removeBlock(landingSupport.above(), false);

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not expose engineer goal for bridge-span test");
            return;
        }

        state.salvageEngineeringBlock(
                Blocks.DIRT.defaultBlockState(),
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
        );

        if (engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie committed to a two-block bridge with only one material block");
            return;
        }

        state.salvageEngineeringBlock(
                Blocks.DIRT.defaultBlockState(),
                SwarmConfig.ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS.get()
        );

        if (!engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie rejected bounded two-block bridge after sufficient material became available");
            return;
        }

        engineer.start();
        for (int i = 0; i < 8; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (helper.getLevel().getBlockState(firstSupport).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not place first block of accepted two-block bridge");
            return;
        }

        zombie.setPos(zombie.getX() + 1.0, zombie.getY(), zombie.getZ());
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 4.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                4,
                0,
                0,
                -1,
                0.0,
                0L
        );

        if (!engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not continue the preflighted bridge onto its second support");
            return;
        }

        engineer.start();
        for (int i = 0; i < 8; i++) {
            engineer.tick();
        }
        engineer.stop();

        if (helper.getLevel().getBlockState(secondSupport).isAir()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Zombie did not complete second block of bounded bridge");
            return;
        }

        if (state.engineeringBlocksPlaced() != 2L
                || state.carriedEngineeringBlockCount() != 0) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Two-block bridge placement did not consume exactly two carried materials");
            return;
        }

        server.setDifficulty(previousDifficulty, true);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_handoff", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void swarmApproachGoalYieldsNearMeleeRange(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setNoGravity(true);

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 farPosition = helper.absoluteVec(new Vec3(8.0, 1.0, 2.0));
        player.setPos(farPosition.x, farPosition.y, farPosition.z);

        helper.runAfterDelay(4, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            state.rememberTarget(player.getUUID(), helper.getTick(), true);
            state.updateLocalPlan(
                    0,
                    0,
                    SwarmRole.CHASER,
                    player.getX(),
                    player.getZ(),
                    0.0,
                    0.0
            );
            zombie.setTarget(player);

            SwarmApproachGoal approachGoal = zombie.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);

            if (approachGoal == null) {
                helper.fail("SwarmApproachGoal was not available for handoff test");
                return;
            }

            if (!approachGoal.canUse()) {
                helper.fail("SwarmApproachGoal should own movement while target is outside release distance");
                return;
            }

            player.setPos(zombie.getX() + 1.0, zombie.getY(), zombie.getZ());

            if (approachGoal.canUse()) {
                helper.fail("SwarmApproachGoal should yield movement inside release distance");
                return;
            }

            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_wall_blocked_handoff", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void closeWallBlockedTargetDoesNotTriggerVanillaMeleeHandoff(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        player.setNoGravity(true);
        player.setPos(zombie.getX() + 2.5, zombie.getY(), zombie.getZ());

        BlockPos obstacle = BlockPos.containing(
                zombie.getX() + 0.9,
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(obstacle.above(), Blocks.DIRT.defaultBlockState());

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(player.getUUID(), helper.getTick(), true);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                player.getX(),
                player.getZ(),
                0.0,
                0.0
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.RECOVERY,
                6,
                4,
                1,
                1,
                5,
                0.25,
                6L
        );
        zombie.setTarget(player);

        SwarmApproachGoal approachGoal = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmApproachGoal.class::isInstance)
                .map(SwarmApproachGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (approachGoal == null) {
            playerHandle.close();
            helper.fail("Zombie did not expose SwarmApproachGoal");
            return;
        }

        if (zombie.hasLineOfSight(player)) {
            playerHandle.close();
            helper.fail("Fixture wall did not block Zombie line of sight");
            return;
        }

        if (!approachGoal.canUse()) {
            playerHandle.close();
            helper.fail("Wall-blocked close target incorrectly triggered vanilla melee handoff");
            return;
        }

        if (state.plannerContext() != SwarmPlannerContext.RECOVERY) {
            playerHandle.close();
            helper.fail("Wall-blocked handoff check unexpectedly cleared recovery telemetry");
            return;
        }

        playerHandle.close();
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_true_neighbor_radius", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void diagonalPeerOutsideTrueNeighborRadiusIsExcluded(GameTestHelper helper) {
        double previousRadius = SwarmConfig.NEIGHBOR_RADIUS.get();
        SwarmConfig.NEIGHBOR_RADIUS.set(2.0);

        Zombie self = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie diagonal = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        self.setNoGravity(true);
        diagonal.setNoGravity(true);

        if (self.distanceTo(diagonal) <= 2.0) {
            SwarmConfig.NEIGHBOR_RADIUS.set(previousRadius);
            helper.fail("Neighbor-radius fixture did not place peer outside true radius");
            return;
        }

        helper.runAfterDelay(18, () -> {
            SwarmAgentState state = self.getData(SwarmAttachments.AGENT_STATE.get());
            if (state.neighborCount() != 0) {
                SwarmConfig.NEIGHBOR_RADIUS.set(previousRadius);
                helper.fail("AABB-corner peer incorrectly counted inside true neighbor radius");
                return;
            }

            SwarmConfig.NEIGHBOR_RADIUS.set(previousRadius);
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_true_target_radius", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void diagonalPlayerOutsideTrueTargetRadiusIsNotDirectlyObserved(GameTestHelper helper) {
        double previousRadius = SwarmConfig.TARGET_RADIUS.get();
        SwarmConfig.TARGET_RADIUS.set(2.0);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(3.0, 1.0, 3.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);

        if (zombie.distanceTo(player) <= 2.0) {
            playerHandle.close();
            SwarmConfig.TARGET_RADIUS.set(previousRadius);
            helper.fail("Target-radius fixture did not place player outside true radius");
            return;
        }

        helper.runAfterDelay(24, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            if (state.targetId() != null || state.directObservation()) {
                playerHandle.close();
                SwarmConfig.TARGET_RADIUS.set(previousRadius);
                helper.fail("AABB-corner player incorrectly acquired outside true target radius");
                return;
            }

            playerHandle.close();
            SwarmConfig.TARGET_RADIUS.set(previousRadius);
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_perception", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void visiblePlayerIsAcquiredThroughPerceptionLayer(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(3.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);

        helper.runAfterDelay(12, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());

            if (state.targetId() == null) {
                helper.fail("Visible player was not acquired by swarm perception");
                return;
            }

            if (!state.targetId().equals(player.getUUID())) {
                helper.fail("Swarm perception acquired an unexpected target");
                return;
            }

            if (!state.directObservation()) {
                helper.fail("Visible player should be marked as a direct observation");
                return;
            }

            if (zombie.getTarget() != player) {
                helper.fail("Zombie vanilla target did not synchronize with swarm target");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    private static void restoreEngineeringGameRules(
            net.minecraft.server.MinecraftServer server,
            GameTestHelper helper,
            Difficulty difficulty,
            boolean mobGriefing
    ) {
        server.setDifficulty(difficulty, true);
        helper.getLevel().getGameRules()
                .getRule(GameRules.RULE_MOBGRIEFING)
                .set(mobGriefing, server);
    }

    private static TestPlayerHandle createTickingTestPlayer(GameTestHelper helper, GameType gameType) {
        var level = helper.getLevel();
        var server = level.getServer();
        // Multiple fake players must have different names to avoid player
        // session replacement. Minecraft usernames are at most 16 characters.
        UUID testPlayerId = UUID.randomUUID();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(testPlayerId, "swarm-" + testPlayerId.toString().substring(0, 8)),
                false
        );

        ServerPlayer player = new ServerPlayer(
                server,
                level,
                cookie.gameProfile(),
                cookie.clientInformation()
        );

        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void tick() {
                super.tick();
                player.resetLastActionTime();
            }

            @Override
            public boolean isMemoryConnection() {
                return true;
            }
        };

        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        server.getConnection().getConnections().add(connection);
        player.gameMode.changeGameModeForPlayer(gameType);
        player.connection.chunkSender.sendNextChunks(player);
        player.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
        return new TestPlayerHandle(player, connection);
    }

    private record TestPlayerHandle(ServerPlayer player, Connection connection) {
        private void close() {
            if (player.connection != null) {
                player.connection.disconnect(net.minecraft.network.chat.Component.literal("Swarm GameTest complete"));
            }
            player.level().getServer().getConnection().getConnections().remove(connection);
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_relay", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void occludedZombieReceivesRelayedPlayerTarget(GameTestHelper helper) {
        Zombie relay = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Zombie observer = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        relay.setNoGravity(true);
        observer.setNoGravity(true);

        // A two-block-high wall blocks the relay zombie's direct view toward +X.
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);

        helper.runAfterDelay(20, () -> {
            SwarmAgentState observerState = observer.getData(SwarmAttachments.AGENT_STATE.get());
            SwarmAgentState relayState = relay.getData(SwarmAttachments.AGENT_STATE.get());

            if (!observer.hasLineOfSight(player)) {
                helper.fail("Observer zombie unexpectedly lost direct line of sight to player");
                return;
            }

            if (relay.hasLineOfSight(player)) {
                helper.fail("Relay zombie unexpectedly had direct line of sight through test wall");
                return;
            }

            if (!player.getUUID().equals(observerState.targetId()) || !observerState.directObservation()) {
                helper.fail("Observer zombie did not retain direct player observation");
                return;
            }

            if (!player.getUUID().equals(relayState.targetId())) {
                helper.fail("Occluded zombie did not receive player target from local swarm neighbor");
                return;
            }

            if (relayState.directObservation()) {
                helper.fail("Relayed target was incorrectly marked as direct observation");
                return;
            }

            if (relayState.lastTargetObservationTick() > observerState.lastTargetObservationTick()) {
                helper.fail("Relay target timestamp became newer than the latest direct observation");
                return;
            }

            if (relayState.lastTargetObservationTick() == Long.MIN_VALUE) {
                helper.fail("Relay target did not carry a real observation timestamp");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_memory_expiry", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 220)
    public static void targetMemoryExpiresAfterLineOfSightIsLost(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);
        var movementSpeed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(0.0D);
        }

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);
        double initialObservedX = playerPosition.x;

        helper.runAfterDelay(15, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());

            if (!player.getUUID().equals(state.targetId()) || !state.directObservation()) {
                helper.fail("Zombie did not establish direct target memory before occlusion");
                return;
            }

            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);

            if (zombie.hasLineOfSight(player)) {
                helper.fail("Occlusion wall did not break direct line of sight");
                return;
            }

            player.setPos(player.getX() + 2.0, player.getY(), player.getZ());
        });

        helper.runAfterDelay(35, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());

            if (state.targetObservation() == null || !state.targetObservation().hasFinitePosition()) {
                helper.fail("Occluded zombie lost its last-known target snapshot too early");
                return;
            }

            if (Math.abs(state.targetObservation().x() - initialObservedX) > 0.25) {
                helper.fail("Occluded target snapshot followed the player's live position");
            }
        });

        int expiryCheckTick = 15 + SwarmConfig.TARGET_MEMORY_TICKS.get() + 30;
        helper.runAfterDelay(expiryCheckTick, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());

            if (zombie.hasLineOfSight(player)) {
                helper.fail("Zombie unexpectedly regained line of sight during memory-expiry test");
                return;
            }

            if (state.targetId() != null) {
                helper.fail("Stale swarm target memory did not expire after configured memory window");
                return;
            }

            if (state.hasDestination()) {
                helper.fail("Expired target memory left a stale planned destination");
                return;
            }

            if (zombie.getTarget() instanceof net.minecraft.world.entity.player.Player) {
                helper.fail("Expired swarm memory left a stale vanilla player target");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_heterogeneous_relay", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void zombieObservationRelaysToOccludedSkeletonSupport(GameTestHelper helper) {
        var skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(1, 1, 2));
        Zombie observer = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        skeleton.setNoGravity(true);
        observer.setNoGravity(true);

        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);

        helper.runAfterDelay(30, () -> {
            SwarmAgentState observerState = observer.getData(SwarmAttachments.AGENT_STATE.get());
            SwarmAgentState skeletonState = skeleton.getData(SwarmAttachments.AGENT_STATE.get());

            if (!observer.hasLineOfSight(player)) {
                helper.fail("Zombie observer unexpectedly lost direct line of sight");
                return;
            }

            if (skeleton.hasLineOfSight(player)) {
                helper.fail("Skeleton support unexpectedly had direct line of sight through test wall");
                return;
            }

            if (!player.getUUID().equals(observerState.targetId()) || !observerState.directObservation()) {
                helper.fail("Zombie observer did not establish direct target observation");
                return;
            }

            if (!player.getUUID().equals(skeletonState.targetId())) {
                helper.fail("Skeleton support did not receive target observation from Zombie teammate");
                return;
            }

            if (skeletonState.directObservation()) {
                helper.fail("Cross-species relay was incorrectly marked as direct observation");
                return;
            }

            if (skeletonState.role() != SwarmRole.RANGED_SUPPORT) {
                helper.fail("Skeleton did not enter RANGED_SUPPORT tactical role");
                return;
            }

            if (!skeletonState.hasDestination()) {
                helper.fail("Skeleton support did not receive a planned support destination");
                return;
            }

            boolean hasApproachGoal = skeleton.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.getGoal() instanceof SwarmApproachGoal);
            if (!hasApproachGoal) {
                helper.fail("Skeleton did not receive the heterogeneous SwarmApproachGoal");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_skeleton_creeper_pair", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void skeletonSupportsCreeperBreacherFromDeeperStandoff(GameTestHelper helper) {
        var skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(1, 1, 1));
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(1, 1, 3));
        skeleton.setNoGravity(true);
        creeper.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);

        helper.runAfterDelay(30, () -> {
            SwarmAgentState skeletonState = skeleton.getData(SwarmAttachments.AGENT_STATE.get());
            SwarmAgentState creeperState = creeper.getData(SwarmAttachments.AGENT_STATE.get());

            if (!player.getUUID().equals(skeletonState.targetId())
                    || !player.getUUID().equals(creeperState.targetId())) {
                playerHandle.close();
                helper.fail("Skeleton/Creeper pair did not converge on the same player target");
                return;
            }

            if (skeletonState.role() != SwarmRole.RANGED_SUPPORT) {
                playerHandle.close();
                helper.fail("Skeleton did not retain ranged-support duty beside a breacher");
                return;
            }

            if (creeperState.role() != SwarmRole.CHASER) {
                playerHandle.close();
                helper.fail("Creeper breacher was diverted away from direct-pressure duty");
                return;
            }

            if (!skeletonState.hasDestination() || !creeperState.hasDestination()) {
                playerHandle.close();
                helper.fail("Skeleton/Creeper pair did not receive coordinated destinations");
                return;
            }

            double skeletonStandoff = Math.hypot(
                    skeletonState.destinationX() - player.getX(),
                    skeletonState.destinationZ() - player.getZ()
            );
            double creeperStandoff = Math.hypot(
                    creeperState.destinationX() - player.getX(),
                    creeperState.destinationZ() - player.getZ()
            );

            if (skeletonStandoff <= creeperStandoff + 2.0) {
                playerHandle.close();
                helper.fail("Skeleton support did not form a deeper layer behind Creeper breacher");
                return;
            }

            double ingressX = creeper.getX() - player.getX();
            double ingressZ = creeper.getZ() - player.getZ();
            double ingressLength = Math.hypot(ingressX, ingressZ);
            if (ingressLength > 0.5) {
                double supportX = skeletonState.destinationX() - player.getX();
                double supportZ = skeletonState.destinationZ() - player.getZ();
                double lateralClearance = Math.abs(
                        supportX * ingressZ - supportZ * ingressX
                ) / ingressLength;

                if (lateralClearance < 2.5) {
                    playerHandle.close();
                    helper.fail("Skeleton support destination remained too close to Creeper ingress axis");
                    return;
                }
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_skeleton_bow_handoff", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 170)
    public static void skeletonKeepsVanillaBowAttackInsideRangedEnvelope(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        var skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(1, 1, 2));
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.reassessWeaponGoal();

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);

        final boolean[] observedBowCombat = {false};

        helper.runAfterDelay(20, () -> {
            SwarmAgentState state = skeleton.getData(SwarmAttachments.AGENT_STATE.get());

            if (!player.getUUID().equals(state.targetId()) || !state.directObservation()) {
                playerHandle.close();
                helper.fail("Skeleton did not establish direct swarm observation before bow-handoff check");
                return;
            }

            SwarmApproachGoal approachGoal = skeleton.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);

            if (approachGoal == null) {
                playerHandle.close();
                helper.fail("Skeleton did not retain SwarmApproachGoal");
                return;
            }

            if (approachGoal.canUse()) {
                playerHandle.close();
                helper.fail("SwarmApproachGoal did not yield inside Skeleton bow range");
                return;
            }

            boolean hasBridgeGoal = skeleton.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.getGoal() instanceof dev.swarmmobs.goal.SwarmSkeletonBowGoal);
            if (!hasBridgeGoal) {
                playerHandle.close();
                helper.fail("Skeleton did not receive the high-priority bow bridge goal");
            }
        });

        for (int sampleTick : new int[] {35, 55, 75, 95}) {
            helper.runAfterDelay(sampleTick, () -> {
                if (skeleton.isUsingItem()) {
                    observedBowCombat[0] = true;
                }

                boolean arrowPresent = !skeleton.level().getEntitiesOfClass(
                        AbstractArrow.class,
                        skeleton.getBoundingBox().inflate(24.0),
                        arrow -> arrow.isAlive()
                ).isEmpty();
                if (arrowPresent) {
                    observedBowCombat[0] = true;
                }
            });
        }

        helper.runAfterDelay(110, () -> {
            boolean arrowPresent = !skeleton.level().getEntitiesOfClass(
                    AbstractArrow.class,
                    skeleton.getBoundingBox().inflate(32.0),
                    arrow -> arrow.isAlive()
            ).isEmpty();

            if (!observedBowCombat[0] && !arrowPresent) {
                playerHandle.close();
                helper.fail("Skeleton never drew/fired its bow after swarm ranged handoff");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_creeper_handoff", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void creeperKeepsSwarmApproachThenYieldsToFuse(GameTestHelper helper) {
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(1, 1, 2));
        creeper.setNoGravity(true);

        var movementSpeed = creeper.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(0.0D);
        }

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 farPosition = helper.absoluteVec(new Vec3(5.0, 1.0, 2.0));
        player.setPos(farPosition.x, farPosition.y, farPosition.z);
        player.setNoGravity(true);

        helper.runAfterDelay(18, () -> {
            SwarmAgentState state = creeper.getData(SwarmAttachments.AGENT_STATE.get());

            if (!player.getUUID().equals(state.targetId()) || !state.directObservation()) {
                playerHandle.close();
                helper.fail("Creeper did not establish direct swarm observation");
                return;
            }

            if (!state.hasDestination()) {
                playerHandle.close();
                helper.fail("Creeper did not receive a swarm approach destination");
                return;
            }

            SwarmApproachGoal approachGoal = creeper.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);

            if (approachGoal == null || !approachGoal.canUse()) {
                playerHandle.close();
                helper.fail("Creeper swarm approach was not active outside fuse range");
                return;
            }

            // Exercise the boundary that used to be vulnerable to a dead zone:
            // outside the 3.0-block fuse envelope but inside the generic 3.25
            // melee release distance. Creeper swarm movement must still own MOVE.
            player.setPos(
                    creeper.getX() + SwarmCreeperSwellGoal.HANDOFF_DISTANCE + 0.10,
                    creeper.getY(),
                    creeper.getZ()
            );
        });

        helper.runAfterDelay(26, () -> {
            SwarmApproachGoal approachGoal = creeper.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);

            if (approachGoal == null || !approachGoal.canUse()) {
                playerHandle.close();
                helper.fail("Creeper swarm approach yielded in the pre-fuse boundary band");
                return;
            }

            player.setPos(
                    creeper.getX() + 2.0,
                    creeper.getY(),
                    creeper.getZ()
            );
        });

        helper.runAfterDelay(42, () -> {
            SwarmAgentState state = creeper.getData(SwarmAttachments.AGENT_STATE.get());

            SwarmApproachGoal approachGoal = creeper.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);

            if (approachGoal == null) {
                playerHandle.close();
                helper.fail("Creeper lost SwarmApproachGoal registration");
                return;
            }

            if (creeper.getSwellDir() <= 0) {
                playerHandle.close();
                helper.fail("Creeper never entered vanilla swell/fuse behavior inside handoff range");
                return;
            }

            if (approachGoal.canUse()) {
                playerHandle.close();
                helper.fail("SwarmApproachGoal did not yield after Creeper fuse handoff");
                return;
            }

            if (!state.directObservation()) {
                playerHandle.close();
                helper.fail("Creeper fuse handoff occurred without a direct target observation");
                return;
            }

            creeper.discard();
            playerHandle.close();
            helper.succeed();
        });
    }


    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_master_combat_fallback", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void masterOffReleasesCustomCombatBridges(GameTestHelper helper) {
        boolean previousMaster = SwarmConfig.ENABLED.get();
        SwarmConfig.ENABLED.set(true);

        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(1, 1, 1));
        skeleton.setNoGravity(true);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.reassessWeaponGoal();

        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(1, 1, 3));
        creeper.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        player.setNoGravity(true);
        player.setPos(skeleton.getX() + 2.0, skeleton.getY(), skeleton.getZ());

        SwarmAgentState skeletonState =
                skeleton.getData(SwarmAttachments.AGENT_STATE.get());
        skeletonState.rememberTarget(player.getUUID(), helper.getTick(), true);
        skeleton.setTarget(player);

        var skeletonBridge = skeleton.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(dev.swarmmobs.goal.SwarmSkeletonBowGoal.class::isInstance)
                .map(dev.swarmmobs.goal.SwarmSkeletonBowGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (skeletonBridge == null || !skeletonBridge.canUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            playerHandle.close();
            helper.fail("Skeleton bridge fixture was not active with swarm master ON");
            return;
        }

        player.setPos(creeper.getX() + 2.0, creeper.getY(), creeper.getZ());
        SwarmAgentState creeperState =
                creeper.getData(SwarmAttachments.AGENT_STATE.get());
        creeperState.rememberTarget(player.getUUID(), helper.getTick(), true);
        creeper.setTarget(player);

        SwarmCreeperSwellGoal creeperBridge = creeper.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmCreeperSwellGoal.class::isInstance)
                .map(SwarmCreeperSwellGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (creeperBridge == null || !creeperBridge.canUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            playerHandle.close();
            helper.fail("Creeper bridge fixture was not active with swarm master ON");
            return;
        }

        SwarmConfig.ENABLED.set(false);

        if (skeletonBridge.canUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            playerHandle.close();
            helper.fail("Skeleton custom bow bridge remained active with swarm master OFF");
            return;
        }

        if (creeperBridge.canUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            playerHandle.close();
            helper.fail("Unlit Creeper custom swell bridge remained active with swarm master OFF");
            return;
        }

        // An already-ignited Creeper must not be extinguished merely because the
        // swarm layer is disabled mid-fuse.
        creeper.ignite();
        if (!creeperBridge.canUse()) {
            SwarmConfig.ENABLED.set(previousMaster);
            playerHandle.close();
            helper.fail("Master OFF incorrectly interrupted an already-ignited Creeper fuse");
            return;
        }

        creeper.discard();
        skeleton.discard();
        SwarmConfig.ENABLED.set(previousMaster);
        playerHandle.close();
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_zombie_melee_handoff", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void zombieActuallyAttacksAfterSwarmMeleeHandoff(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        player.setPos(zombie.getX() + 1.25, zombie.getY(), zombie.getZ());
        player.setNoGravity(true);
        float initialHealth = player.getHealth();

        helper.runAfterDelay(20, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            if (!player.getUUID().equals(state.targetId()) || !state.directObservation()) {
                playerHandle.close();
                helper.fail("Zombie did not establish direct swarm observation before melee handoff");
                return;
            }

            SwarmApproachGoal approachGoal = zombie.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);

            if (approachGoal == null) {
                playerHandle.close();
                helper.fail("Zombie lost SwarmApproachGoal registration");
                return;
            }

            if (approachGoal.canUse()) {
                playerHandle.close();
                helper.fail("SwarmApproachGoal did not yield inside Zombie melee range");
            }
        });

        helper.runAfterDelay(70, () -> {
            if (player.getHealth() >= initialHealth) {
                playerHandle.close();
                helper.fail("Zombie never landed a vanilla melee attack after swarm handoff");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_spider_melee_handoff", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 150)
    public static void spiderActuallyAttacksAfterSwarmMeleeHandoff(GameTestHelper helper) {
        // Allow extra scheduling margin on slower local development machines while
        // preserving the strong success criterion: vanilla Spider melee must actually
        // reduce the test player's health after swarm movement yields.
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int i = 0; i <= 4; i++) {
                helper.setBlock(new BlockPos(0, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(4, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 4), Blocks.STONE);
            }
        }

        var spider = helper.spawn(EntityType.SPIDER, new BlockPos(1, 1, 2));
        spider.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        player.setPos(spider.getX() + 1.25, spider.getY(), spider.getZ());
        player.setNoGravity(true);
        float initialHealth = player.getHealth();

        helper.runAfterDelay(20, () -> {
            SwarmAgentState state = spider.getData(SwarmAttachments.AGENT_STATE.get());
            if (!player.getUUID().equals(state.targetId()) || !state.directObservation()) {
                playerHandle.close();
                helper.fail("Spider did not establish direct swarm observation before melee handoff");
                return;
            }

            SwarmApproachGoal approachGoal = spider.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);

            if (approachGoal == null) {
                playerHandle.close();
                helper.fail("Spider lost SwarmApproachGoal registration");
                return;
            }

            if (approachGoal.canUse()) {
                playerHandle.close();
                helper.fail("SwarmApproachGoal did not yield inside Spider melee range");
            }
        });

        helper.runAfterDelay(110, () -> {
            if (player.getHealth() >= initialHealth) {
                playerHandle.close();
                helper.fail("Spider never landed a vanilla melee attack after swarm handoff");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_division_of_labor", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void skeletonTeamDifferentiatesRangedSpecializations(GameTestHelper helper) {
        Skeleton skeletonA = helper.spawn(EntityType.SKELETON, new BlockPos(1, 1, 1));
        Skeleton skeletonB = helper.spawn(EntityType.SKELETON, new BlockPos(1, 1, 2));
        Skeleton skeletonC = helper.spawn(EntityType.SKELETON, new BlockPos(1, 1, 3));
        skeletonA.setNoGravity(true);
        skeletonB.setNoGravity(true);
        skeletonC.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);
        player.setInvulnerable(true);

        // Target acquisition itself is covered by other Runtime GameTests.
        // This fixture isolates division-of-labor behavior by giving the
        // same live target to all three ranged agents.
        skeletonA.setTarget(player);
        skeletonB.setTarget(player);
        skeletonC.setTarget(player);

        // Formation hysteresis (20 ticks) and specialization hold (30 ticks)
        // are intentionally sequential when peers acquire the target on
        // staggered planning ticks. Test the settled team, not initial startup.
        helper.runAfterDelay(70, () -> {
            java.util.List<Skeleton> skeletons = java.util.List.of(
                    skeletonA,
                    skeletonB,
                    skeletonC
            );
            java.util.Set<dev.swarmmobs.agent.SwarmSpecialization> specializations =
                    new java.util.HashSet<>();

            for (Skeleton skeleton : skeletons) {
                SwarmAgentState state =
                        skeleton.getData(SwarmAttachments.AGENT_STATE.get());

                if (!player.getUUID().equals(state.targetId())) {
                    playerHandle.close();
                    helper.fail("Ranged division-of-labor team did not converge on shared target");
                    return;
                }

                if (state.currentTask() != dev.swarmmobs.agent.SwarmTaskType.RANGED_SUPPORT) {
                    playerHandle.close();
                    helper.fail("Skeleton did not select ranged-support task under engage demand");
                    return;
                }

                if (state.role() != SwarmRole.RANGED_SUPPORT) {
                    playerHandle.close();
                    helper.fail("Dynamic specialization rewrote Skeleton out of ranged combat role");
                    return;
                }

                specializations.add(state.specialization());
            }

            if (specializations.size() < 2) {
                playerHandle.close();
                helper.fail("Same-species Skeleton team failed to differentiate ranged specializations");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_spider_flanker", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void spiderJoinsSharedSwarmAsFlanker(GameTestHelper helper) {
        var spider = helper.spawn(EntityType.SPIDER, new BlockPos(2, 1, 2));
        spider.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(5.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);

        helper.runAfterDelay(24, () -> {
            SwarmAgentState state = spider.getData(SwarmAttachments.AGENT_STATE.get());

            if (!player.getUUID().equals(state.targetId())) {
                helper.fail("Spider flanker did not acquire the shared player target");
                return;
            }

            if (state.role() != SwarmRole.FLANK_LEFT && state.role() != SwarmRole.FLANK_RIGHT) {
                helper.fail("Spider was assigned a non-flanker tactical role");
                return;
            }

            if (!state.hasDestination()) {
                helper.fail("Spider flanker did not receive a planned flank destination");
                return;
            }

            boolean hasApproachGoal = spider.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.getGoal() instanceof SwarmApproachGoal);
            if (!hasApproachGoal) {
                helper.fail("Spider did not receive the heterogeneous SwarmApproachGoal");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_composition_roles", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void dedicatedSpidersReleaseZombieFromFlankDuty(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        var spiderA = helper.spawn(EntityType.SPIDER, new BlockPos(1, 1, 2));
        var spiderB = helper.spawn(EntityType.SPIDER, new BlockPos(3, 1, 2));
        zombie.setNoGravity(true);
        spiderA.setNoGravity(true);
        spiderB.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(5.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);

        helper.runAfterDelay(36, () -> {
            SwarmAgentState zombieState = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            SwarmAgentState spiderAState = spiderA.getData(SwarmAttachments.AGENT_STATE.get());
            SwarmAgentState spiderBState = spiderB.getData(SwarmAttachments.AGENT_STATE.get());

            if (!player.getUUID().equals(zombieState.targetId())
                    || !player.getUUID().equals(spiderAState.targetId())
                    || !player.getUUID().equals(spiderBState.targetId())) {
                helper.fail("Mixed local team did not converge on the same player target");
                return;
            }

            if (zombieState.role() == SwarmRole.FLANK_LEFT
                    || zombieState.role() == SwarmRole.FLANK_RIGHT) {
                helper.fail("Zombie kept a flank role despite two dedicated Spider flankers");
                return;
            }

            boolean spiderAFlanking = spiderAState.role() == SwarmRole.FLANK_LEFT
                    || spiderAState.role() == SwarmRole.FLANK_RIGHT;
            boolean spiderBFlanking = spiderBState.role() == SwarmRole.FLANK_LEFT
                    || spiderBState.role() == SwarmRole.FLANK_RIGHT;
            boolean spiderAInterceptor =
                    spiderAState.specialization() == dev.swarmmobs.agent.SwarmSpecialization.INTERCEPTOR
                            && spiderAState.role() == SwarmRole.CHASER;
            boolean spiderBInterceptor =
                    spiderBState.specialization() == dev.swarmmobs.agent.SwarmSpecialization.INTERCEPTOR
                            && spiderBState.role() == SwarmRole.CHASER;

            if (!(spiderAFlanking || spiderBFlanking)) {
                helper.fail("Dynamic Spider team lost all dedicated flank coverage");
                return;
            }

            if ((!spiderAFlanking && !spiderAInterceptor)
                    || (!spiderBFlanking && !spiderBInterceptor)) {
                helper.fail("Spider specialization left both flank and interceptor responsibilities");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_search_mode", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 220)
    public static void staleOccludedSpiderTransitionsIntoSearchMode(GameTestHelper helper) {
        var spider = helper.spawn(EntityType.SPIDER, new BlockPos(1, 1, 2));
        spider.setNoGravity(true);

        var movementSpeed = spider.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(0.0D);
        }

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);
        double lastVisibleX = playerPosition.x;

        helper.runAfterDelay(15, () -> {
            SwarmAgentState state = spider.getData(SwarmAttachments.AGENT_STATE.get());

            if (!player.getUUID().equals(state.targetId()) || !state.directObservation()) {
                helper.fail("Spider did not establish a direct observation before search-mode test");
                return;
            }

            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
            player.setPos(player.getX() + 2.0, player.getY(), player.getZ());
        });

        int searchCheckTick = 15
                + (int) Math.ceil(
                        (1.0 - SwarmConfig.SEARCH_CONFIDENCE_THRESHOLD.get())
                                * SwarmConfig.TARGET_MEMORY_TICKS.get()
                )
                + 12;

        helper.runAfterDelay(searchCheckTick, () -> {
            SwarmAgentState state = spider.getData(SwarmAttachments.AGENT_STATE.get());

            if (spider.hasLineOfSight(player)) {
                helper.fail("Spider unexpectedly regained direct line of sight during search test");
                return;
            }

            if (!player.getUUID().equals(state.targetId())) {
                helper.fail("Spider lost target memory before entering SEARCH");
                return;
            }

            if (state.directObservation()) {
                helper.fail("Stale target was still marked as direct during SEARCH");
                return;
            }

            if (state.behaviorMode() != SwarmBehaviorMode.SEARCH) {
                helper.fail("Stale indirect target did not switch Spider into SEARCH mode");
                return;
            }

            if (state.searchRadius() <= 0.0) {
                helper.fail("SEARCH mode did not expose a positive search radius");
                return;
            }

            if (!state.hasDestination()) {
                helper.fail("SEARCH mode did not produce a search destination");
                return;
            }

            if (state.targetObservation() == null
                    || Math.abs(state.targetObservation().x() - lastVisibleX) > 0.25) {
                helper.fail("SEARCH mode stopped using the last-known target snapshot");
                return;
            }

            double destinationOffset = Math.hypot(
                    state.destinationX() - state.targetObservation().x(),
                    state.destinationZ() - state.targetObservation().z()
            );
            if (destinationOffset < 0.75) {
                helper.fail("SEARCH destination collapsed onto the stale target center");
                return;
            }

            if (!state.searchEpisodeActive() || state.searchEpisodesStarted() <= 0L) {
                helper.fail("Entering SEARCH did not start a search episode metric");
                return;
            }

            helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
            Vec3 reacquisitionPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 2.0));
            player.setPos(
                    reacquisitionPosition.x,
                    reacquisitionPosition.y,
                    reacquisitionPosition.z
            );
        });

        helper.runAfterDelay(searchCheckTick + 18, () -> {
            SwarmAgentState state = spider.getData(SwarmAttachments.AGENT_STATE.get());

            if (!spider.hasLineOfSight(player) || !state.directObservation()) {
                playerHandle.close();
                helper.fail("Spider did not directly reacquire player after SEARCH obstruction was removed");
                return;
            }

            if (state.searchEpisodeActive()) {
                playerHandle.close();
                helper.fail("Search episode remained active after direct reacquisition");
                return;
            }

            if (state.searchEpisodesSucceeded() <= 0L) {
                playerHandle.close();
                helper.fail("Direct reacquisition did not increment search success metric");
                return;
            }

            if (state.lastSearchReacquisitionTicks() <= 0L
                    || state.searchReacquisitionTicksTotal() < state.lastSearchReacquisitionTicks()) {
                playerHandle.close();
                helper.fail("Search reacquisition latency was not recorded");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_obstacle_toggle", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void disablingObstacleAvoidanceClearsActiveDetour(GameTestHelper helper) {
        boolean previousObstacleAvoidance =
                SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.get();
        boolean previousPathEvidence =
                SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get();
        SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(true);
        // This fixture isolates live detour-toggle semantics. Path-evidence
        // quality is covered independently; disabling it here keeps one lateral
        // candidate deterministically feasible across runners.
        SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(false);

        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        player.setPos(zombie.getX() + 3.0, zombie.getY(), zombie.getZ());
        player.setNoGravity(true);

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        state.rememberTarget(player.getUUID(), helper.getTick(), true);
        state.updateLocalPlan(
                0,
                0,
                SwarmRole.CHASER,
                zombie.getX() + 3.0,
                zombie.getZ(),
                0.0,
                0.0
        );
        zombie.setTarget(player);

        BlockPos frontObstacle = BlockPos.containing(
                zombie.getX() + SwarmConfig.NAV_OBSTACLE_LOOKAHEAD.get(),
                zombie.getY(),
                zombie.getZ()
        );
        helper.getLevel().setBlockAndUpdate(
                frontObstacle,
                Blocks.STONE.defaultBlockState()
        );
        helper.getLevel().setBlockAndUpdate(
                frontObstacle.above(),
                Blocks.STONE.defaultBlockState()
        );

        SwarmApproachGoal approachGoal = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmApproachGoal.class::isInstance)
                .map(SwarmApproachGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (approachGoal == null || !approachGoal.canUse()) {
            SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(previousObstacleAvoidance);
            SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(previousPathEvidence);
            playerHandle.close();
            helper.fail("Obstacle-toggle fixture could not start SwarmApproachGoal");
            return;
        }

        zombie.goalSelector.removeGoal(approachGoal);
        approachGoal.start();

        if (state.navigationMode() != SwarmNavigationMode.OBSTACLE_DETOUR) {
            SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(previousObstacleAvoidance);
            SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(previousPathEvidence);
            playerHandle.close();
            helper.fail(
                    "Fixture did not establish an active obstacle detour before toggle"
                            + " mode=" + state.navigationMode()
            );
            return;
        }

        SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(false);
        approachGoal.start();

        if (state.navigationMode() != SwarmNavigationMode.PLAN) {
            SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(previousObstacleAvoidance);
            SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(previousPathEvidence);
            playerHandle.close();
            helper.fail(
                    "Obstacle Avoidance OFF retained stale detour waypoint"
                            + " mode=" + state.navigationMode()
            );
            return;
        }

        SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.set(previousObstacleAvoidance);
        SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.set(previousPathEvidence);
        playerHandle.close();
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_recovery_planner", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void immobileZombieTriggersValidatedRecoveryPlanner(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));

        var movementSpeed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(0.0D);
        }

        TestPlayerHandle playerHandle = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer player = playerHandle.player();
        // Keep the target far enough for SwarmApproachGoal to own movement while
        // leaving at least the forward-left and forward-right recovery candidates
        // inside the 5x5 GameTest floor. The previous axial layout placed every
        // default-distance recovery candidate on/outside the floor, correctly
        // producing zero feasible candidates and testing the fixture rather than
        // the recovery planner.
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.0, 1.0, 4.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setNoGravity(true);
        final SwarmApproachGoal[] recoveryGoal = new SwarmApproachGoal[1];

        helper.runAfterDelay(4, () -> {
            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            state.rememberTarget(player.getUUID(), helper.getTick(), true);
            state.updateLocalPlan(
                    0,
                    0,
                    SwarmRole.CHASER,
                    player.getX(),
                    player.getZ(),
                    0.0,
                    0.0
            );
            zombie.setTarget(player);

            SwarmApproachGoal approachGoal = zombie.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);

            if (approachGoal == null || !approachGoal.canUse()) {
                playerHandle.close();
                helper.fail("SwarmApproachGoal was not eligible before forced recovery test");
                return;
            }

            zombie.goalSelector.removeGoal(approachGoal);
            recoveryGoal[0] = approachGoal;
            approachGoal.start();
        });

        int recoveryCheckTick = 4 + SwarmConfig.NAV_STUCK_WINDOW_TICKS.get() + 4;
        helper.runAfterDelay(recoveryCheckTick, () -> {
            SwarmApproachGoal approachGoal = recoveryGoal[0];
            if (approachGoal == null) {
                playerHandle.close();
                helper.fail("SwarmApproachGoal was not retained for forced recovery test");
                return;
            }

            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            state.rememberTarget(player.getUUID(), helper.getTick(), true);
            state.updateLocalPlan(
                    0,
                    0,
                    SwarmRole.CHASER,
                    player.getX(),
                    player.getZ(),
                    0.0,
                    0.0
            );
            zombie.setTarget(player);

            approachGoal.tick();

            if (state.recoveryPlanningAttempts() <= 0L) {
                playerHandle.close();
                helper.fail("Forced immobility did not trigger recovery planning");
                return;
            }

            if (state.recoveryCount() <= 0L) {
                playerHandle.close();
                helper.fail(
                        "Recovery planner never committed a feasible recovery waypoint"
                                + " candidates=" + state.plannerCandidateCount()
                                + " blocked=" + state.plannerBlockedCount()
                                + " unreachable=" + state.plannerUnreachableCount()
                                + " feasible=" + state.plannerFeasibleCount()
                                + " selected=" + state.plannerSelectedIndex()
                );
                return;
            }

            if (state.plannerPathQueryCount() <= 0L && SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get()) {
                playerHandle.close();
                helper.fail("Recovery planner did not record PathNavigation evidence queries");
                return;
            }

            playerHandle.close();
            helper.succeed();
        });
    }


    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_budget_deferral", templateNamespace = SwarmMobs.MOD_ID,
            template = TEMPLATE, timeoutTicks = 80)
    public static void budgetDeniedObstacleDoesNotIssueUnverifiedDirectMove(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setNoGravity(true);

        // The forward probe must hit this wall. Both lateral candidates near
        // z=1 and z=4 stay open on the 5x5 test floor.
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);

        helper.runAfterDelay(2, () -> {
            if (!SwarmConfig.NAV_OBSTACLE_AVOIDANCE_ENABLED.get()
                    || !SwarmConfig.NAV_PATH_EVIDENCE_ENABLED.get()) {
                helper.fail("Path-budget integration test requires both navigation features enabled");
                return;
            }

            var level = helper.getLevel();
            SwarmApproachGoal approachGoal = zombie.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmApproachGoal.class::isInstance)
                    .map(SwarmApproachGoal.class::cast)
                    .findFirst()
                    .orElse(null);
            if (approachGoal == null) {
                helper.fail("Missing Zombie SwarmApproachGoal");
                return;
            }
            zombie.goalSelector.removeGoal(approachGoal);

            SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            state.rememberTarget(UUID.randomUUID(), helper.getTick(), false);
            state.updateLocalPlan(
                    0, 0, SwarmRole.CHASER,
                    zombie.getX() + 3.0, zombie.getZ(),
                    0.0, 0.0
            );

            int cap = SwarmConfig.NAV_PATH_EVIDENCE_BUDGET_PER_TICK.get();
            var budget = SwarmPathBudgetRegistry.snapshot(level);
            int remaining = Math.max(0, cap - budget.reservedTokens());
            if (remaining > 0 && !SwarmPathBudgetRegistry.reserve(level, zombie, remaining)) {
                helper.fail("Could not reserve remaining path budget for saturation fixture");
                return;
            }
            long issuedBefore = SwarmNavigationCommandTelemetry.snapshot(level).issued();
            approachGoal.start();
            long issuedAfter = SwarmNavigationCommandTelemetry.snapshot(level).issued();
            if (issuedAfter != issuedBefore) {
                approachGoal.stop();
                helper.fail("Budget denial issued an unverified direct move command");
                return;
            }
            if (state.plannerContext() != SwarmPlannerContext.NONE) {
                approachGoal.stop();
                helper.fail("Budget denial published fake path feasibility telemetry");
                return;
            }

            approachGoal.stop();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_two_player_squads", templateNamespace = SwarmMobs.MOD_ID,
            template = TEMPLATE, timeoutTicks = 110)
    public static void twoPlayersCreateIndependentTacticalSquadsThenMerge(GameTestHelper helper) {
        if (!SwarmConfig.ENABLED.get()) {
            helper.fail("Two-player tactical test requires swarm master enabled");
            return;
        }

        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(1, 1, 1));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(4, 1, 1));
        for (var mob : java.util.List.of(skeleton, zombie, creeper)) {
            mob.setNoGravity(true);
            // Stationary members make this a test of live goal planning rather
            // than which player the pathfinder happened to walk toward first.
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        }

        TestPlayerHandle alpha = createTickingTestPlayer(helper, GameType.SURVIVAL);
        TestPlayerHandle beta = createTickingTestPlayer(helper, GameType.SURVIVAL);
        ServerPlayer playerA = alpha.player();
        ServerPlayer playerB = beta.player();
        playerA.setNoGravity(true);
        playerB.setNoGravity(true);
        var aPos = helper.absoluteVec(new Vec3(1.5, 3.0, 4.5));
        var bPos = helper.absoluteVec(new Vec3(4.5, 3.0, 4.5));
        playerA.setPos(aPos.x, aPos.y, aPos.z);
        playerB.setPos(bPos.x, bPos.y, bPos.z);

        helper.runAfterDelay(27, () -> {
            var skeletonState = skeleton.getData(SwarmAttachments.AGENT_STATE.get());
            var zombieState = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            var creeperState = creeper.getData(SwarmAttachments.AGENT_STATE.get());

            if (!playerA.getUUID().equals(skeletonState.targetId())
                    || !playerA.getUUID().equals(zombieState.targetId())
                    || !playerB.getUUID().equals(creeperState.targetId())) {
                alpha.close();
                beta.close();
                helper.fail("Nearby monsters failed to choose their respective closer players");
                return;
            }
            if (!playerA.getUUID().equals(skeletonState.tacticalAssignmentTarget())
                    || !playerA.getUUID().equals(zombieState.tacticalAssignmentTarget())
                    || !playerB.getUUID().equals(creeperState.tacticalAssignmentTarget())) {
                alpha.close();
                beta.close();
                helper.fail("Initial target-scoped tactical binding mismatch");
                return;
            }
            if (skeletonState.tacticalPeerCount() != 1
                    || skeletonState.tacticalBreacherCount() != 0
                    || zombieState.tacticalPeerCount() != 1
                    || creeperState.tacticalPeerCount() != 0) {
                alpha.close();
                beta.close();
                helper.fail("Different target Creeper leaked into Skeleton/Zombie tactical squad"
                        + " skeleton peers=" + skeletonState.tacticalPeerCount()
                        + " breachers=" + skeletonState.tacticalBreacherCount()
                        + " zombie peers=" + zombieState.tacticalPeerCount()
                        + " creeper peers=" + creeperState.tacticalPeerCount());
                return;
            }
            // The physical neighborhood is still shared, which is essential
            // for separation and communication despite different target IDs.
            if (skeletonState.neighborCount() < 2 || creeperState.neighborCount() < 2) {
                alpha.close();
                beta.close();
                helper.fail("Target-scoped tactical filtering incorrectly removed physical neighbors");
                return;
            }

            // B becomes ineligible without teleporting outside the loaded
            // GameTest region. All three now choose the remaining valid A,
            // so next planning cycles should form one mixed tactical squad.
            playerB.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
        });

        helper.runAfterDelay(58, () -> {
            var skeletonState = skeleton.getData(SwarmAttachments.AGENT_STATE.get());
            var zombieState = zombie.getData(SwarmAttachments.AGENT_STATE.get());
            var creeperState = creeper.getData(SwarmAttachments.AGENT_STATE.get());

            if (!playerA.getUUID().equals(skeletonState.targetId())
                    || !playerA.getUUID().equals(zombieState.targetId())
                    || !playerA.getUUID().equals(creeperState.targetId())) {
                alpha.close();
                beta.close();
                helper.fail("Mixed swarm did not converge on remaining directly visible player");
                return;
            }
            if (!playerA.getUUID().equals(skeletonState.tacticalAssignmentTarget())
                    || !playerA.getUUID().equals(zombieState.tacticalAssignmentTarget())
                    || !playerA.getUUID().equals(creeperState.tacticalAssignmentTarget())) {
                alpha.close();
                beta.close();
                helper.fail("Tactical assignment target stayed bound to the old player");
                return;
            }
            if (skeletonState.tacticalPeerCount() != 2
                    || skeletonState.tacticalBreacherCount() != 1
                    || zombieState.tacticalPeerCount() != 2
                    || creeperState.tacticalPeerCount() != 2) {
                alpha.close();
                beta.close();
                helper.fail("Same-target mixed squad did not merge after target change"
                        + " skeleton peers=" + skeletonState.tacticalPeerCount()
                        + " breachers=" + skeletonState.tacticalBreacherCount()
                        + " zombie peers=" + zombieState.tacticalPeerCount()
                        + " creeper peers=" + creeperState.tacticalPeerCount());
                return;
            }

            alpha.close();
            beta.close();
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_foundation", templateNamespace = SwarmMobs.MOD_ID,
            template = TEMPLATE, timeoutTicks = 80)
    public static void idleLocalGroupCanFoundOneOptInNestCore(GameTestHelper helper) {
        Zombie founder = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        Zombie allyOne = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Zombie allyTwo = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
        founder.setNoGravity(true);
        allyOne.setNoGravity(true);
        allyTwo.setNoGravity(true);

        boolean beforeEnabled = SwarmConfig.NEST_CONSTRUCTION_ENABLED.get();
        boolean beforeMobGriefing = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        int beforePopulation = SwarmConfig.NEST_MIN_GROUP_SIZE.get();

        try {
            // The standard GameTest floor is not natural soil. Make exactly
            // one candidate site suitable for a persistent nest marker.
            helper.setBlock(new BlockPos(4, 0, 2), Blocks.DIRT);
            SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(false);
            SwarmConfig.NEST_MIN_GROUP_SIZE.set(3);

            SwarmIdleNestGoal goal = founder.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SwarmIdleNestGoal.class::isInstance)
                    .map(SwarmIdleNestGoal.class::cast)
                    .findFirst().orElse(null);
            if (goal == null || goal.canUse()) {
                helper.fail("Disabled nesting incorrectly attempted world changes");
                return;
            }

            SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(false, helper.getLevel().getServer());
            if (goal.canUse()) {
                helper.fail("Nest founding ignored mobGriefing=false");
                return;
            }

            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());
            if (!goal.canUse()) {
                helper.fail("Eligible idle group could not claim a natural-soil nest site");
                return;
            }

            goal.start();
            var corePosition = helper.absolutePos(new BlockPos(4, 1, 2));
            if (!helper.getLevel().getBlockState(corePosition).is(SwarmNestBlocks.NEST_CORE.get())) {
                helper.fail("Idle colony did not persistently place a Nest Core block");
                return;
            }
            var founded = founder.getData(SwarmAttachments.AGENT_STATE.get()).nestsFounded();
            if (founded != 1L) {
                helper.fail("Nest founding telemetry did not record exactly one placement");
                return;
            }
            // A second attempt must not create a second nest nearby.
            var duplicate = new SwarmIdleNestGoal(allyOne);
            if (duplicate.canUse()) {
                helper.fail("Nearby existing nest was not respected by new builder");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.NEST_CONSTRUCTION_ENABLED.set(beforeEnabled);
            SwarmConfig.NEST_MIN_GROUP_SIZE.set(beforePopulation);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(beforeMobGriefing, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_resource_accounting",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void colonyNestResourceStorageIsFiniteAndRequiresRealMaterial(GameTestHelper helper) {
        BlockPos localCore = new BlockPos(2, 1, 2);
        helper.setBlock(localCore, SwarmNestBlocks.NEST_CORE.get());
        var core = helper.getLevel().getBlockEntity(helper.absolutePos(localCore));
        if (!(core instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Nest Core block has no persistent colony block entity");
            return;
        }

        if (nest.resources() != 0 || nest.births() != 0) {
            helper.fail("New colony core unexpectedly started with free resources");
            return;
        }

        // Resource-classification checks use real vanilla items, including
        // the animal drops players expect colony foragers to collect later.
        if (SwarmNestBlockEntity.classify(new ItemStack(Items.MUTTON))
                    != SwarmNestColonyPolicy.Kind.NUTRIENT
                || SwarmNestBlockEntity.classify(new ItemStack(Items.RABBIT))
                    != SwarmNestColonyPolicy.Kind.NUTRIENT
                || SwarmNestBlockEntity.classify(new ItemStack(Items.COOKED_BEEF))
                    != SwarmNestColonyPolicy.Kind.NUTRIENT
                || SwarmNestBlockEntity.classify(new ItemStack(Items.OAK_LOG))
                    != SwarmNestColonyPolicy.Kind.TIMBER
                || SwarmNestBlockEntity.classify(new ItemStack(Items.DIRT))
                    != SwarmNestColonyPolicy.Kind.SOIL) {
            helper.fail("Vanilla food, wood and soil classification drifted");
            return;
        }

        int acceptedLogs = nest.deposit(SwarmNestColonyPolicy.Kind.TIMBER, 2);
        int acceptedFood = nest.deposit(SwarmNestColonyPolicy.Kind.NUTRIENT, 1);
        int rejected = nest.deposit(SwarmNestColonyPolicy.Kind.NONE, 100);
        if (acceptedLogs != 2 || acceptedFood != 1 || rejected != 0
                || nest.resources() != 10
                || nest.soilPoints() != 0
                || nest.timberPoints() != 6
                || nest.nutrientPoints() != 4
                || nest.legacyPoints() != 0) {
            helper.fail("Nest storage did not conserve deposited material");
            return;
        }

        int acceptedExtra = nest.deposit(SwarmNestColonyPolicy.Kind.NUTRIENT, 1000);
        // 10 + 29 * 4 = 126. Two spare units are insufficient for
        // another four-unit nutrient item; no fractional matter is created.
        if (acceptedExtra != 29 || nest.resources() != 126
                || nest.timberPoints() != 6 || nest.nutrientPoints() != 120
                || nest.deposit(SwarmNestColonyPolicy.Kind.NUTRIENT, 1) != 0) {
            helper.fail("Nest resources exceeded their fixed storage capacity");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_growth",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void resourceFedColonyBirthIsBoundedAndConsumesSupplies(GameTestHelper helper) {
        BlockPos corePos = new BlockPos(2, 1, 2);
        helper.setBlock(corePos, SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(new BlockPos(4, 0, 2), Blocks.DIRT);

        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(corePos));
        if (!(blockEntity instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Functional Nest Core was not registered as a BlockEntity");
            return;
        }

        boolean oldLifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        int oldCap = SwarmConfig.NEST_MAX_POPULATION.get();
        boolean oldSpawning = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_DOMOBSPAWNING);
        Difficulty oldDifficulty = helper.getLevel().getDifficulty();
        TestPlayerHandle observer = createTickingTestPlayer(helper, GameType.SURVIVAL);

        try {
            // The simulated player is 18 blocks away: within the 48-block
            // activity range, outside the 12-block no-spawn safety radius.
            ServerPlayer player = observer.player();
            player.setNoGravity(true);
            Vec3 location = helper.absoluteVec(new Vec3(20.0, 1.0, 2.0));
            player.setPos(location.x, location.y, location.z);

            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_MAX_POPULATION.set(12);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING)
                    .set(true, helper.getLevel().getServer());
            helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);

            // Construction supplies must NEVER be convertible into nutrition.
            if (nest.deposit(SwarmNestColonyPolicy.Kind.SOIL, 12) != 12) {
                helper.fail("Nest could not store natural-soil building supplies");
                return;
            }
            nest.runColonyCycle(helper.getLevel());
            if (nest.births() != 0 || nest.nutrientPoints() != 0
                    || nest.soilPoints() != 12) {
                helper.fail("A soil-only colony illegally generated a new monster");
                return;
            }

            // Three real nutrient items provide precisely one 12-point birth.
            if (nest.deposit(SwarmNestColonyPolicy.Kind.NUTRIENT, 3) != 3) {
                helper.fail("Nest could not accept required nutrient input");
                return;
            }

            nest.runColonyCycle(helper.getLevel());
            if (nest.births() != 1L || nest.resources() != 12
                    || nest.soilPoints() != 12 || nest.nutrientPoints() != 0) {
                helper.fail("One nutrient-funded colony birth failed to conserve construction materials"
                        + " births=" + nest.births() + " reserve=" + nest.resources());
                return;
            }

            nest.runColonyCycle(helper.getLevel());
            if (nest.births() != 1L) {
                helper.fail("Colony cooldown failed to prevent immediate repeat spawning");
                return;
            }

            nest.deposit(SwarmNestColonyPolicy.Kind.NUTRIENT, 3);
            SwarmConfig.NEST_MAX_POPULATION.set(3);
            if (nest.births() != 1L) {
                helper.fail("Unexpected birth before next lifecycle cycle");
                return;
            }
            helper.succeed();
        } finally {
            observer.close();
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(oldLifecycle);
            SwarmConfig.NEST_MAX_POPULATION.set(oldCap);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING)
                    .set(oldSpawning, helper.getLevel().getServer());
            helper.getLevel().getServer().setDifficulty(oldDifficulty, true);
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_architecture",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void colonyChamberExpansionConsumesSoilAndTimberAndRaisesCapacity(GameTestHelper helper) {
        BlockPos corePos = new BlockPos(2, 1, 2);
        helper.setBlock(corePos, SwarmNestBlocks.NEST_CORE.get());
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(corePos));
        if (!(entity instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Colony architecture core is not a saved block entity");
            return;
        }
        for (BlockPos pos : java.util.List.of(
                new BlockPos(1, 1, 1),
                new BlockPos(3, 1, 1),
                new BlockPos(1, 1, 3))) {
            Zombie member = helper.spawn(EntityType.ZOMBIE, pos);
            member.setNoAi(true);
            member.setNoGravity(true);
        }

        TestPlayerHandle observer = createTickingTestPlayer(helper, GameType.SURVIVAL);
        boolean previous = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        int previousCap = SwarmConfig.NEST_MAX_POPULATION.get();
        String stage = "starting";
        try {
            stage = "placing observer";
            var player = observer.player();
            player.setNoGravity(true);
            Vec3 point = helper.absoluteVec(new Vec3(20.0, 1.0, 2.0));
            player.setPos(point.x, point.y, point.z);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_MAX_POPULATION.set(12);

            // Eight dirt and two log items: 8 soil + 6 timber resource points.
            stage = "depositing soil and timber";
            if (nest.deposit(SwarmNestColonyPolicy.Kind.SOIL, 8) != 8
                    || nest.deposit(SwarmNestColonyPolicy.Kind.TIMBER, 2) != 2) {
                helper.fail("Colony could not store its construction bill of materials");
                return;
            }
            if (nest.chamberLevel() != 0 || nest.effectiveCapacity() != 4) {
                helper.fail("New nest should start with a four-member core capacity");
                return;
            }

            stage = "first colony sample and upgrade";
            nest.runColonyCycle(helper.getLevel());
            stage = "checking material accounting";
            if (nest.chamberLevel() != 1 || nest.effectiveCapacity() != 8
                    || nest.soilPoints() != 0 || nest.timberPoints() != 0
                    || nest.resources() != 0 || nest.lastPopulation() != 3) {
                helper.fail("Nest room upgrade did not consume exact supplies or expand capacity"
                        + " rooms=" + nest.chamberLevel()
                        + " capacity=" + nest.effectiveCapacity()
                        + " reserve=" + nest.resources()
                        + " observedPopulation=" + nest.lastPopulation());
                return;
            }
            nest.runColonyCycle(helper.getLevel());
            if (nest.chamberLevel() != 1) {
                helper.fail("Nest added a free second chamber with zero materials");
                return;
            }
            helper.succeed();
        } catch (RuntimeException cause) {
            String trace = java.util.Arrays.toString(cause.getStackTrace());
            helper.fail("Colony architecture failed during [" + stage + "]: "
                    + cause + " stack=" + trace.substring(0, Math.min(1400, trace.length())));
        } finally {
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(previous);
            SwarmConfig.NEST_MAX_POPULATION.set(previousCap);
            observer.close();
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_visible_shell",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void colonyVisibleShellRequiresLoadedFreeSpaceAndRealMaterials(GameTestHelper helper) {
        BlockPos corePos = new BlockPos(2, 1, 2);
        BlockPos soilPos = new BlockPos(4, 1, 2);
        BlockPos timberPos = new BlockPos(0, 1, 2);
        helper.setBlock(corePos, SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(new BlockPos(4, 0, 2), Blocks.DIRT);
        helper.setBlock(new BlockPos(0, 0, 2), Blocks.DIRT);
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(corePos));
        if (!(entity instanceof SwarmNestBlockEntity nest)) {
            helper.fail("No persistent Nest Core block entity for visible shell");
            return;
        }
        for (BlockPos pos : java.util.List.of(
                new BlockPos(1, 1, 1),
                new BlockPos(3, 1, 1),
                new BlockPos(1, 1, 3))) {
            Zombie member = helper.spawn(EntityType.ZOMBIE, pos);
            member.setNoAi(true);
            member.setNoGravity(true);
        }

        TestPlayerHandle observer = createTickingTestPlayer(helper, GameType.SURVIVAL);
        boolean oldLifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean oldVisible = SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.get();
        boolean oldGrief = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        int oldCap = SwarmConfig.NEST_MAX_POPULATION.get();
        try {
            ServerPlayer player = observer.player();
            player.setNoGravity(true);
            Vec3 farAway = helper.absoluteVec(new Vec3(20.0, 1.0, 2.0));
            player.setPos(farAway.x, farAway.y, farAway.z);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.set(true);
            SwarmConfig.NEST_MAX_POPULATION.set(12);
            nest.deposit(SwarmNestColonyPolicy.Kind.SOIL, 8);
            nest.deposit(SwarmNestColonyPolicy.Kind.TIMBER, 2);

            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(false, helper.getLevel().getServer());
            nest.runColonyCycle(helper.getLevel());
            if (nest.chamberLevel() != 0 || nest.visibleChamberLevel() != 0
                    || nest.resources() != 14) {
                helper.fail("Visible construction violated mobGriefing=false");
                return;
            }

            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());
            helper.setBlock(soilPos, Blocks.STONE);
            nest.runColonyCycle(helper.getLevel());
            if (nest.chamberLevel() != 0 || nest.resources() != 14
                    || !helper.getLevel().getBlockState(helper.absolutePos(soilPos)).is(Blocks.STONE)) {
                helper.fail("Obstructed nest shell site was overwritten or charged materials");
                return;
            }

            helper.setBlock(soilPos, Blocks.AIR);
            nest.runColonyCycle(helper.getLevel());
            if (nest.chamberLevel() != 1 || nest.visibleChamberLevel() != 1
                    || nest.effectiveCapacity() != 8 || nest.resources() != 0
                    || !helper.getLevel().getBlockState(helper.absolutePos(soilPos)).is(Blocks.DIRT)
                    || !helper.getLevel().getBlockState(helper.absolutePos(timberPos)).is(Blocks.OAK_LOG)
                    || !nest.ownsShellPiece(helper.absolutePos(soilPos))
                    || !nest.ownsShellPiece(helper.absolutePos(timberPos))
                    || nest.ownsShellPiece(helper.absolutePos(new BlockPos(3,1,3)))) {
                helper.fail("Safe nest shell failed to place raw dirt / oak-log modules"
                        + " chambers=" + nest.chamberLevel()
                        + " visual=" + nest.visibleChamberLevel()
                        + " reserve=" + nest.resources());
                return;
            }

            nest.runColonyCycle(helper.getLevel());
            if (nest.chamberLevel() != 1 || nest.visibleChamberLevel() != 1) {
                helper.fail("Construction gained extra modules without new supplies");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(oldLifecycle);
            SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.set(oldVisible);
            SwarmConfig.NEST_MAX_POPULATION.set(oldCap);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(oldGrief, helper.getLevel().getServer());
            observer.close();
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_hauling",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void zombieCarriesExistingLogDropToHomeWithoutCreatingExtraItems(
            GameTestHelper helper) {
        BlockPos corePos = new BlockPos(0, 1, 2);
        helper.setBlock(corePos, SwarmNestBlocks.NEST_CORE.get());
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(corePos));
        if (!(entity instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Real haul test has no Nest Core");
            return;
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(corePos).asLong());
        Vec3 dropPos = helper.absoluteVec(new Vec3(4.9, 1.2, 2.5));
        ItemEntity physicalDrop = new ItemEntity(
                helper.getLevel(), dropPos.x, dropPos.y, dropPos.z,
                new ItemStack(Items.OAK_LOG, 2));
        helper.getLevel().addFreshEntity(physicalDrop);

        boolean wasEnabled = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean wasLifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean wasMaster = SwarmConfig.ENABLED.get();
        boolean wasPheromones = SwarmConfig.NEST_PHEROMONES_ENABLED.get();
        boolean wasGriefing = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_PHEROMONES_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());

            SwarmZombieColonyHaulGoal goal = worker.goalSelector.getAvailableGoals()
                    .stream().map(wrapped -> wrapped.getGoal())
                    .filter(SwarmZombieColonyHaulGoal.class::isInstance)
                    .map(SwarmZombieColonyHaulGoal.class::cast)
                    .findFirst().orElse(null);
            if (goal == null || !goal.canUse()) {
                helper.fail("Idle enrolled worker failed to recognize a real dropped log");
                return;
            }

            goal.start();
            goal.tick(); // Material remains an ItemEntity while picked up.
            if (!physicalDrop.isAlive() || physicalDrop.getItem().getCount() != 2
                    || !SwarmNestHaulLease.isClaimed(
                            physicalDrop, helper.getLevel().getGameTime())
                    || nest.resources() != 0) {
                helper.fail("Physical cargo unexpectedly vanished or became free inventory");
                return;
            }

            // Move worker to the destination: it must deposit the SAME
            // ItemEntity, without spawning a synthetic replacement item.
            Vec3 dock = helper.absoluteVec(new Vec3(1.1, 1.0, 2.5));
            worker.setPos(dock.x, dock.y, dock.z);
            goal.tick();
            goal.stop();
            if (physicalDrop.isAlive() || nest.timberPoints() != 6
                    || nest.resources() != 6 || nest.hauledItems() != 2
                    || nest.haulTrips() != 1
                    || nest.pheromones().reinforcements() < 1
                    || nest.pheromones().strength(
                            new SwarmNestPheromoneField.Position(
                                    helper.absolutePos(new BlockPos(4,1,2)).getX(),
                                    helper.absolutePos(new BlockPos(4,1,2)).getY(),
                                    helper.absolutePos(new BlockPos(4,1,2)).getZ()),
                            SwarmNestPheromoneField.Signal.TIMBER,
                            helper.getLevel().getGameTime()) <= 0.0) {
                helper.fail("Worker item delivery failed exact accounting"
                        + " timber=" + nest.timberPoints()
                        + " hauled=" + nest.hauledItems()
                        + " trips=" + nest.haulTrips());
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.NEST_HAULING_ENABLED.set(wasEnabled);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(wasLifecycle);
            SwarmConfig.ENABLED.set(wasMaster);
            SwarmConfig.NEST_PHEROMONES_ENABLED.set(wasPheromones);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(wasGriefing, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_haul_abort",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void workerInterruptedDuringHaulDoesNotDeleteItsRealCargo(
            GameTestHelper helper) {
        BlockPos corePos = new BlockPos(0, 1, 2);
        helper.setBlock(corePos, SwarmNestBlocks.NEST_CORE.get());
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(corePos));
        if (!(entity instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Interrupted worker fixture missing Nest Core");
            return;
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(corePos).asLong());
        Vec3 pos = helper.absoluteVec(new Vec3(4.9, 1.2, 2.5));
        ItemEntity physicalDrop = new ItemEntity(
                helper.getLevel(), pos.x, pos.y, pos.z,
                new ItemStack(Items.DIRT, 4));
        helper.getLevel().addFreshEntity(physicalDrop);

        boolean wasEnabled = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean wasLifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean wasMaster = SwarmConfig.ENABLED.get();
        boolean wasGriefing = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());
            var goal = new SwarmZombieColonyHaulGoal(worker);
            if (!goal.canUse()) {
                helper.fail("Worker failed to claim interrupted drop");
                return;
            }
            goal.start();
            goal.tick();
            // Simulate a higher-priority interruption before nest arrival.
            SwarmConfig.NEST_HAULING_ENABLED.set(false);
            if (goal.canContinueToUse()) {
                helper.fail("Disabled hauling kept controlling the worker");
                return;
            }
            goal.stop();
            if (!physicalDrop.isAlive() || physicalDrop.getItem().getCount() != 4
                    || SwarmNestHaulLease.isClaimed(
                            physicalDrop, helper.getLevel().getGameTime())
                    || nest.resources() != 0 || nest.haulTrips() != 0) {
                helper.fail("Interrupted job lost cargo, duplicated it or kept a stale lease");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.NEST_HAULING_ENABLED.set(wasEnabled);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(wasLifecycle);
            SwarmConfig.ENABLED.set(wasMaster);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(wasGriefing, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_rehoming",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void idleWorkerRehomesFromDestroyedLoadedCoreWithoutGlobalSearch(
            GameTestHelper helper) {
        BlockPos original = new BlockPos(0, 1, 2);
        BlockPos replacement = new BlockPos(4, 1, 2);
        helper.setBlock(original, SwarmNestBlocks.NEST_CORE.get());
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        worker.setNoGravity(true);

        TestPlayerHandle observer = createTickingTestPlayer(helper, GameType.SURVIVAL);
        boolean oldLifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean oldMaster = SwarmConfig.ENABLED.get();
        try {
            ServerPlayer player = observer.player();
            player.setNoGravity(true);
            Vec3 distant = helper.absoluteVec(new Vec3(20.0, 1.0, 2.0));
            player.setPos(distant.x, distant.y, distant.z);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.ENABLED.set(true);

            var originalEntity = helper.getLevel().getBlockEntity(helper.absolutePos(original));
            if (!(originalEntity instanceof SwarmNestBlockEntity first)) {
                helper.fail("First home was not a functional Nest Core");
                return;
            }
            first.runColonyCycle(helper.getLevel());
            if (!worker.getPersistentData().contains("SwarmColonyNest")
                    || worker.getPersistentData().getLong("SwarmColonyNest")
                            != helper.absolutePos(original).asLong()) {
                helper.fail("First core failed to enroll nearby idle worker");
                return;
            }
            helper.setBlock(original, Blocks.AIR);
            helper.setBlock(replacement, SwarmNestBlocks.NEST_CORE.get());
            var replacementEntity = helper.getLevel().getBlockEntity(helper.absolutePos(replacement));
            if (!(replacementEntity instanceof SwarmNestBlockEntity second)) {
                helper.fail("Replacement home has no colony block entity");
                return;
            }
            second.runColonyCycle(helper.getLevel());
            if (worker.getPersistentData().getLong("SwarmColonyNest")
                    != helper.absolutePos(replacement).asLong()) {
                helper.fail("Worker kept a demolished home despite a nearby valid replacement");
                return;
            }
            helper.succeed();
        } finally {
            observer.close();
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(oldLifecycle);
            SwarmConfig.ENABLED.set(oldMaster);
        }
    }


    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_spider_scout",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void roamingSpiderObservesRealItemForItsOwnNest(
            GameTestHelper helper) {
        BlockPos corePos = new BlockPos(0, 1, 2);
        helper.setBlock(corePos, SwarmNestBlocks.NEST_CORE.get());
        var core = helper.getLevel().getBlockEntity(helper.absolutePos(corePos));
        if (!(core instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Spider scout fixture missing core");
            return;
        }
        Spider scout = helper.spawn(EntityType.SPIDER, new BlockPos(3, 1, 2));
        scout.setNoGravity(true);
        scout.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(corePos).asLong());
        Vec3 origin = helper.absoluteVec(new Vec3(4.9, 1.2, 2.5));
        ItemEntity original = new ItemEntity(
                helper.getLevel(), origin.x, origin.y, origin.z,
                new ItemStack(Items.OAK_LOG, 2));
        helper.getLevel().addFreshEntity(original);

        boolean enabled = SwarmConfig.ENABLED.get();
        boolean lifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean hauling = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean griefing = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());

            boolean installed = scout.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.getGoal()
                            instanceof SwarmSpiderColonyScoutGoal);
            if (!installed) {
                helper.fail("Spider did not receive colony scouting goal");
                return;
            }
            var goal = new SwarmSpiderColonyScoutGoal(scout);
            if (!goal.canUse()) {
                helper.fail("Loaded idle Spider did not schedule bounded item survey");
                return;
            }
            goal.start();
            if (!SwarmNestScoutSignal.recentFor(
                    original, helper.absolutePos(corePos), helper.getLevel().getGameTime())
                    || nest.scoutBoard().size(helper.getLevel().getGameTime()) != 1
                    || original.getItem().getCount() != 2 || !original.isAlive()
                    || nest.resources() != 0) {
                helper.fail("Spider sensing must mark, not consume, the real item");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(enabled);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(lifecycle);
            SwarmConfig.NEST_HAULING_ENABLED.set(hauling);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(griefing, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_remote_scout_handoff",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void spiderReportDispatchesOneZombieBeyondDirectItemSearch(
            GameTestHelper helper) {
        BlockPos corePos = new BlockPos(0, 1, 2);
        helper.setBlock(corePos, SwarmNestBlocks.NEST_CORE.get());
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(corePos));
        if (!(entity instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Remote scout dispatch fixture has no core");
            return;
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(corePos).asLong());
        Vec3 dropPos = helper.absoluteVec(new Vec3(4.99, 1.2, 2.5));
        ItemEntity realItem = new ItemEntity(
                helper.getLevel(), dropPos.x, dropPos.y, dropPos.z,
                new ItemStack(Items.OAK_LOG, 2));
        helper.getLevel().addFreshEntity(realItem);

        boolean wasEnabled = SwarmConfig.ENABLED.get();
        boolean wasLifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean wasHauling = SwarmConfig.NEST_HAULING_ENABLED.get();
        int wasRadius = SwarmConfig.NEST_HAUL_SEARCH_RADIUS.get();
        boolean wasGriefing = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_HAUL_SEARCH_RADIUS.set(4);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());
            if (!nest.reportScoutItem(realItem, helper.getLevel().getGameTime())) {
                helper.fail("Loaded core did not accept Spider-like real resource report");
                return;
            }
            var goal = new SwarmZombieColonyHaulGoal(worker);
            if (!goal.canUse()) {
                helper.fail("Worker did not reserve remote scout report");
                return;
            }
            var currentPos = worker.blockPosition();
            if (nest.scoutBoard().reserve(UUID.randomUUID(),
                    new dev.swarmmobs.colony.SwarmNestScoutBoard.Position(
                            currentPos.getX(), currentPos.getY(), currentPos.getZ()),
                    helper.getLevel().getGameTime(), 28) != null) {
                helper.fail("Two workers reserved the same remote item lead");
                goal.stop();
                return;
            }
            goal.start();
            if (!goal.canContinueToUse()) {
                helper.fail("Remote waypoint was incorrectly rejected without nearby item");
                goal.stop();
                return;
            }
            // Explicit position changes test lookup + exact cargo conservation.
            // An independent natural-pathfinding GameTest is still needed.
            worker.setPos(dropPos.x - .3, dropPos.y, dropPos.z);
            goal.tick();
            if (!realItem.isAlive() || !SwarmNestHaulLease.isClaimed(
                    realItem, helper.getLevel().getGameTime())) {
                helper.fail("Remote report did not resolve and claim original world item");
                goal.stop();
                return;
            }
            Vec3 dock = helper.absoluteVec(new Vec3(1.1, 1.0, 2.5));
            worker.setPos(dock.x, dock.y, dock.z);
            goal.tick();
            goal.stop();
            if (realItem.isAlive() || nest.resources() != 6
                    || nest.timberPoints() != 6 || nest.hauledItems() != 2
                    || nest.haulTrips() != 1
                    || nest.scoutBoard().size(helper.getLevel().getGameTime()) != 0) {
                helper.fail("Spider waypoint did not deliver exactly two real logs");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(wasEnabled);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(wasLifecycle);
            SwarmConfig.NEST_HAULING_ENABLED.set(wasHauling);
            SwarmConfig.NEST_HAUL_SEARCH_RADIUS.set(wasRadius);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(wasGriefing, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_renewable_berry_forage",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void ripeBushProducesPhysicalFoodThenZombieHaulsItToCore(
            GameTestHelper helper) {
        BlockPos corePos = new BlockPos(0, 1, 2);
        BlockPos plantPos = new BlockPos(4, 1, 2);
        helper.setBlock(corePos, SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(plantPos.below(), Blocks.DIRT);
        helper.setBlock(plantPos,
                Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                        .setValue(SweetBerryBushBlock.AGE, 3));
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(corePos));
        if (!(blockEntity instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Forage fixture missing live colony core");
            return;
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(corePos).asLong());

        boolean master = SwarmConfig.ENABLED.get();
        boolean lifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean hauling = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean foraging = SwarmConfig.NEST_BERRY_FORAGING_ENABLED.get();
        boolean grief = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_BERRY_FORAGING_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());

            boolean installed = worker.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.getGoal()
                            instanceof SwarmZombieBerryForageGoal);
            if (!installed) {
                helper.fail("Colony worker is missing safe renewable foraging goal");
                return;
            }
            var forage = new SwarmZombieBerryForageGoal(worker);
            if (!forage.canUse()) {
                helper.fail("Idle worker did not locate mature berry bush");
                return;
            }
            forage.start();
            forage.tick();
            forage.stop();

            var after = helper.getLevel().getBlockState(helper.absolutePos(plantPos));
            if (!after.is(Blocks.SWEET_BERRY_BUSH)
                    || after.getValue(SweetBerryBushBlock.AGE) != 1
                    || nest.resources() != 0 || nest.nutrientPoints() != 0
                    || nest.foragedBerries() != 2) {
                helper.fail("Picking did not conserve a renewable bush and real food accounting");
                return;
            }
            var drops = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, new AABB(helper.absolutePos(plantPos)).inflate(2.0),
                    candidate -> candidate.isAlive()
                            && candidate.getItem().is(Items.SWEET_BERRIES));
            if (drops.size() != 1 || drops.getFirst().getItem().getCount() != 2) {
                helper.fail("Foraging must create exactly two physical berry items");
                return;
            }
            ItemEntity physicalBerries = drops.getFirst();
            // The regular transport subsystem now accepts that same item.
            var haul = new SwarmZombieColonyHaulGoal(worker);
            if (!haul.canUse()) {
                helper.fail("Hauler could not claim the actually produced berry drop");
                return;
            }
            haul.start();
            haul.tick();
            Vec3 dock = helper.absoluteVec(new Vec3(1.1, 1.0, 2.5));
            worker.setPos(dock.x, dock.y, dock.z);
            haul.tick();
            haul.stop();
            if (physicalBerries.isAlive() || nest.nutrientPoints() != 8
                    || nest.resources() != 8 || nest.hauledItems() != 2
                    || nest.haulTrips() != 1 || nest.foragedBerries() != 2) {
                helper.fail("Existing real-item hauler failed to deliver foraged food exactly");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(lifecycle);
            SwarmConfig.NEST_HAULING_ENABLED.set(hauling);
            SwarmConfig.NEST_BERRY_FORAGING_ENABLED.set(foraging);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_tree_mining",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void workerCutsRawLogAndProducesPhysicalTimber(GameTestHelper helper) {
        var nestPos = new BlockPos(0, 1, 2);
        var harvestPos = new BlockPos(4, 1, 2);
        helper.setBlock(nestPos, SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(harvestPos, Blocks.OAK_LOG);
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(nestPos));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("No core for log mining"); return;
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(nestPos).asLong());
        boolean oldMaster = SwarmConfig.ENABLED.get();
        boolean oldLife = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean oldHaul = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean oldGather = SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get();
        boolean oldGrief = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());
            var goal = new SwarmZombieColonyGatherGoal(worker);
            if (!goal.canUse()) { helper.fail("Worker cannot choose nearby oak log"); return; }
            goal.start(); goal.tick(); goal.stop();
            if (!helper.getLevel().getBlockState(helper.absolutePos(harvestPos)).isAir()
                    || nest.resources() != 0) {
                helper.fail("Log not mined or resources added without transport"); return;
            }
            var drops = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, new AABB(helper.absolutePos(harvestPos)).inflate(2),
                    e -> e.isAlive() && e.getItem().is(Items.OAK_LOG));
            if (drops.size() != 1 || drops.getFirst().getItem().getCount() != 1) {
                helper.fail("Raw log must exist as one physical loot item"); return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(oldMaster);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(oldLife);
            SwarmConfig.NEST_HAULING_ENABLED.set(oldHaul);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(oldGather);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(oldGrief, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_soil_mining",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void workerMinesRealDirtWithoutMagicallyCreditingNest(GameTestHelper helper) {
        var nestPos = new BlockPos(0, 1, 2);
        var site = new BlockPos(4, 1, 2);
        helper.setBlock(nestPos, SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(site, Blocks.DIRT);
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(nestPos));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("No core for soil mining"); return;
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(nestPos).asLong());
        boolean master = SwarmConfig.ENABLED.get(), life = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean hauling = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean gather = SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get();
        boolean grief = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());
            var goal = new SwarmZombieColonyGatherGoal(worker);
            if (!goal.canUse()) { helper.fail("Worker cannot target genuine soil"); return; }
            goal.start(); goal.tick(); goal.stop();
            var drops = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, new AABB(helper.absolutePos(site)).inflate(2),
                    e -> e.isAlive() && e.getItem().is(Items.DIRT));
            if (!helper.getLevel().getBlockState(helper.absolutePos(site)).isAir()
                    || drops.size() != 1 || nest.soilPoints() != 0) {
                helper.fail("Dirt must be mined into real loot before any delivery"); return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(life);
            SwarmConfig.NEST_HAULING_ENABLED.set(hauling);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(gather);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_crop_harvest",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void workerHarvestsOnlyMatureCarrotsAsWorldItems(GameTestHelper helper) {
        var nestPos = new BlockPos(0, 1, 2);
        var site = new BlockPos(4, 1, 2);
        helper.setBlock(nestPos, SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(site.below(), Blocks.FARMLAND);
        helper.setBlock(site, Blocks.CARROTS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CarrotBlock.AGE, 7));
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(nestPos));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Missing core for crop harvest"); return;
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(nestPos).asLong());
        boolean master = SwarmConfig.ENABLED.get(), life = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean hauling = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean gather = SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get();
        boolean grief = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());
            var goal = new SwarmZombieColonyGatherGoal(worker);
            if (!goal.canUse()) { helper.fail("Ripe carrots not selected"); return; }
            goal.start(); goal.tick(); goal.stop();
            var drops = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, new AABB(helper.absolutePos(site)).inflate(2),
                    e -> e.isAlive() && e.getItem().is(Items.CARROT));
            int produced = drops.stream().mapToInt(e -> e.getItem().getCount()).sum();
            if (!helper.getLevel().getBlockState(helper.absolutePos(site)).isAir()
                    || produced <= 0 || nest.nutrientPoints() != 0
                    || SwarmNestBlockEntity.classify(drops.getFirst().getItem())
                            != SwarmNestColonyPolicy.Kind.NUTRIENT) {
                helper.fail("Mature crop must yield real edible items without free inventory");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(life);
            SwarmConfig.NEST_HAULING_ENABLED.set(hauling);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(gather);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_pig_hunting",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void workerHuntsRealPigAndLeavesVanillaMeatToHaul(GameTestHelper helper) {
        var nestPos = new BlockPos(0, 1, 2);
        helper.setBlock(nestPos, SwarmNestBlocks.NEST_CORE.get());
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(nestPos));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Pig hunting fixture missing core"); return;
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(nestPos).asLong());
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 2));
        pig.setNoGravity(true);
        pig.setHealth(1.0f);
        boolean master = SwarmConfig.ENABLED.get(), life = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean hauling = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean hunt = SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.get();
        boolean grief = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean mobLoot = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT)
                    .set(true, helper.getLevel().getServer());
            var goal = new SwarmZombieColonyHuntGoal(worker);
            if (!goal.canUse()) { helper.fail("Worker could not claim adult pig"); return; }
            goal.start(); goal.tick(); goal.stop();
            var meat = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, new AABB(pig.blockPosition()).inflate(3),
                    e -> e.isAlive() && e.getItem().is(Items.PORKCHOP));
            if (pig.isAlive() || meat.isEmpty()
                    || SwarmNestBlockEntity.classify(meat.getFirst().getItem())
                            != SwarmNestColonyPolicy.Kind.NUTRIENT
                    || nest.nutrientPoints() != 0) {
                helper.fail("Real pig must die from normal melee, dropping actual meat"); return;
            }
            ItemEntity actualMeat = meat.getFirst();
            int realMeatCount = actualMeat.getItem().getCount();
            var haul = new SwarmZombieColonyHaulGoal(worker);
            if (!haul.canUse()) {
                helper.fail("Pig food could not enter normal hauling workflow"); return;
            }
            haul.start();
            haul.tick();
            Vec3 dock = helper.absoluteVec(new Vec3(1.1, 1.0, 2.5));
            worker.setPos(dock.x, dock.y, dock.z);
            haul.tick();
            haul.stop();
            if (actualMeat.isAlive() || nest.haulTrips() != 1
                    || nest.hauledItems() != realMeatCount
                    || nest.nutrientPoints() != realMeatCount * 4) {
                helper.fail("Real pig meat was lost or duplicated between hunt and nest");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(life);
            SwarmConfig.NEST_HAULING_ENABLED.set(hauling);
            SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(hunt);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief, helper.getLevel().getServer());
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT)
                    .set(mobLoot, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_worker_site_lease",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void oneRealLogWorkSiteCannotBeTakenByTwoZombieWorkers(
            GameTestHelper helper) {
        BlockPos nestPos = new BlockPos(0, 1, 2);
        BlockPos logPos = new BlockPos(4, 1, 2);
        helper.setBlock(nestPos, SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(logPos, Blocks.OAK_LOG);
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(nestPos));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Colony work-site fixture missing core"); return;
        }
        Zombie first = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        Zombie second = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        first.setNoGravity(true);
        second.setNoGravity(true);
        long nestId = helper.absolutePos(nestPos).asLong();
        first.getPersistentData().putLong("SwarmColonyNest", nestId);
        second.getPersistentData().putLong("SwarmColonyNest", nestId);

        boolean master = SwarmConfig.ENABLED.get();
        boolean lifecycle = SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean hauling = SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean gathering = SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get();
        boolean grief = helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, helper.getLevel().getServer());

            var a = new SwarmZombieColonyGatherGoal(first);
            var b = new SwarmZombieColonyGatherGoal(second);
            if (!a.canUse()) {
                helper.fail("First worker failed to claim the only log work site"); return;
            }
            long now = helper.getLevel().getGameTime();
            if (nest.workBoard().size(now) != 1) {
                helper.fail("One log must create precisely one reservation"); return;
            }
            if (b.canUse()) {
                b.stop();
                helper.fail("Second worker duplicated the active log assignment");
                a.stop();
                return;
            }
            a.start();
            a.tick();
            a.stop();
            if (!helper.getLevel().getBlockState(helper.absolutePos(logPos)).isAir()
                    || nest.workBoard().size(now) != 0 || nest.resources() != 0) {
                helper.fail("Exclusive work job did not yield exactly one physical mined site");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(lifecycle);
            SwarmConfig.NEST_HAULING_ENABLED.set(hauling);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(gathering);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief, helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_return_signal",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void physicalSuccessfulHaulRecruitsWithoutGrantingVirtualItems(
            GameTestHelper helper) {
        BlockPos nestPos = new BlockPos(0, 1, 2);
        helper.setBlock(nestPos, SwarmNestBlocks.NEST_CORE.get());
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(nestPos));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Return feedback fixture missing core"); return;
        }
        Vec3 pos = helper.absoluteVec(new Vec3(1.0, 1.5, 2.5));
        ItemEntity original = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z,
                new ItemStack(Items.OAK_LOG, 2));
        helper.getLevel().addFreshEntity(original);
        long now = helper.getLevel().getGameTime();
        double before = nest.laborFeedback().costFactor(
                SwarmNestColonyPolicy.Kind.TIMBER, now);
        int accepted = nest.acceptHaulDelivery(original, 16);
        double after = nest.laborFeedback().costFactor(
                SwarmNestColonyPolicy.Kind.TIMBER, now);
        if (accepted != 2 || original.isAlive() || nest.timberPoints() != 6
                || nest.haulTrips() != 1 || nest.resources() != 6
                || nest.laborFeedback().successes() != 1
                || !(after < before)
                || nest.laborFeedback().costFactor(
                        SwarmNestColonyPolicy.Kind.NUTRIENT, now) != 1.0) {
            helper.fail("Returning physical cargo failed local, category-specific reinforcement");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_living_resource_scent",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void spiderMarksRealPigAndRipeCropWithoutProducingPhantomFood(
            GameTestHelper helper) {
        BlockPos core = new BlockPos(0,1,2);
        BlockPos crop = new BlockPos(4,1,2);
        helper.setBlock(core, SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop,Blocks.CARROTS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CarrotBlock.AGE,7));
        var entity=helper.getLevel().getBlockEntity(helper.absolutePos(core));
        if (!(entity instanceof SwarmNestBlockEntity nest)) {
            helper.fail("No loaded nest for spatial scouting");return;
        }
        Spider scout=helper.spawn(EntityType.SPIDER,new BlockPos(3,1,2));
        scout.setNoGravity(true);
        scout.getPersistentData().putLong("SwarmColonyNest",helper.absolutePos(core).asLong());
        Pig pig=helper.spawn(EntityType.PIG,new BlockPos(4,1,3));
        pig.setNoGravity(true);
        boolean master=SwarmConfig.ENABLED.get();
        boolean lifecycle=SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean hauling=SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean gather=SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get();
        boolean hunt=SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.get();
        boolean pheromones=SwarmConfig.NEST_PHEROMONES_ENABLED.get();
        boolean grief=helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(true);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(true);
            SwarmConfig.NEST_PHEROMONES_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true,helper.getLevel().getServer());
            var goal = new SwarmSpiderColonyScoutGoal(scout);
            if (!goal.canUse()) {
                helper.fail("Idle Spider could not perform local scent survey");
                return;
            }
            goal.start();
            long now=helper.getLevel().getGameTime();
            var p=helper.absolutePos(crop);
            if (!pig.isAlive() || !helper.getLevel().getBlockState(p).is(Blocks.CARROTS)
                    || nest.nutrientPoints()!=0 || nest.resources()!=0
                    || nest.pheromones().observations()<2
                    || nest.pheromones().strength(
                        new SwarmNestPheromoneField.Position(p.getX(),p.getY(),p.getZ()),
                        SwarmNestPheromoneField.Signal.FOOD,now)<=0) {
                helper.fail("Spider scout must signal real crop/prey without consuming them");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(lifecycle);
            SwarmConfig.NEST_HAULING_ENABLED.set(hauling);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(gather);
            SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(hunt);
            SwarmConfig.NEST_PHEROMONES_ENABLED.set(pheromones);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief,helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_scent_navigation",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void idleZombieFollowsLocalScentButNotAbsentSignals(
            GameTestHelper helper) {
        BlockPos core=new BlockPos(0,1,2);
        BlockPos position=new BlockPos(0,1,0);
        BlockPos destination=new BlockPos(4,1,0);
        helper.setBlock(core,SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(destination.below(),Blocks.STONE);
        var be=helper.getLevel().getBlockEntity(helper.absolutePos(core));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("No core for gradient test");return;
        }
        Zombie worker=helper.spawn(EntityType.ZOMBIE,position);
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",helper.absolutePos(core).asLong());
        boolean master=SwarmConfig.ENABLED.get();
        boolean lifecycle=SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean hauling=SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean pheromones=SwarmConfig.NEST_PHEROMONES_ENABLED.get();
        boolean exploration=SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.get();
        boolean grief=helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_PHEROMONES_ENABLED.set(true);
            SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true,helper.getLevel().getServer());
            var signal=new SwarmNestPheromoneField.Position(
                    helper.absolutePos(destination).getX(),
                    helper.absolutePos(destination).getY(),
                    helper.absolutePos(destination).getZ());
            var scentCore=helper.absolutePos(core);
            var homePos=new SwarmNestPheromoneField.Position(
                    scentCore.getX(),scentCore.getY(),scentCore.getZ());
            if (!nest.pheromones().observe(homePos,signal,
                    SwarmNestColonyPolicy.Kind.TIMBER,helper.getLevel().getGameTime())) {
                helper.fail("Failed to emit a loaded local wood source cue");
                return;
            }
            boolean installed=worker.goalSelector.getAvailableGoals().stream()
                    .anyMatch(w->w.getGoal() instanceof SwarmZombiePheromoneExploreGoal);
            var goal=new SwarmZombiePheromoneExploreGoal(worker);
            if (!installed || !goal.canUse()) {
                helper.fail("Worker did not choose a nearby gradient when food/wood absent");
                return;
            }
            goal.start();
            goal.stop();
            if (nest.resources()!=0 || nest.pheromones().size(helper.getLevel().getGameTime())==0) {
                helper.fail("Gradient walking invented resources or destroyed signal");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(lifecycle);
            SwarmConfig.NEST_HAULING_ENABLED.set(hauling);
            SwarmConfig.NEST_PHEROMONES_ENABLED.set(pheromones);
            SwarmConfig.NEST_PHEROMONE_EXPLORATION_ENABLED.set(exploration);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief,helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_emergent_resource_choice",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void sharedSwarmThresholdsAndPheromonePreferDiscoveredLogSite(
            GameTestHelper helper) {
        BlockPos core = new BlockPos(0,1,2);
        BlockPos left = new BlockPos(0,1,0);
        BlockPos right = new BlockPos(4,1,0);
        helper.setBlock(core,SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(left,Blocks.OAK_LOG);
        helper.setBlock(right,Blocks.OAK_LOG);
        var block=helper.getLevel().getBlockEntity(helper.absolutePos(core));
        if (!(block instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Missing nest for emergent work choice");return;
        }
        Zombie worker=helper.spawn(EntityType.ZOMBIE,new BlockPos(2,1,0));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(core).asLong());
        boolean wasMaster=SwarmConfig.ENABLED.get();
        boolean wasLife=SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean wasHaul=SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean wasGather=SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get();
        boolean wasPheromones=SwarmConfig.NEST_PHEROMONES_ENABLED.get();
        boolean wasGrief=helper.getLevel().getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(true);
            SwarmConfig.NEST_PHEROMONES_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true,helper.getLevel().getServer());
            long tick=helper.getLevel().getGameTime();
            if (!nest.markPheromone(helper.absolutePos(right),
                    SwarmNestColonyPolicy.Kind.TIMBER,tick)) {
                helper.fail("Worker did not receive real site scent");return;
            }
            // Both blocks are equally distant. The concentrated real signal
            // at the right-hand log must break the tie without giving wood
            // magically to the core or removing the other candidate.
            var goal=new SwarmZombieColonyGatherGoal(worker);
            if (!goal.canUse()) {
                helper.fail("Worker cannot choose between real log sites");return;
            }
            goal.start();
            goal.tick();
            goal.stop();
            if (!helper.getLevel().getBlockState(helper.absolutePos(right)).isAir()
                    || !helper.getLevel().getBlockState(helper.absolutePos(left)).is(Blocks.OAK_LOG)
                    || nest.resources()!=0) {
                helper.fail("Local scent did not guide the worker to the preferred physical log");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(wasMaster);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(wasLife);
            SwarmConfig.NEST_HAULING_ENABLED.set(wasHaul);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(wasGather);
            SwarmConfig.NEST_PHEROMONES_ENABLED.set(wasPheromones);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(wasGrief,helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_remote_log_job",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void spiderWorkReportDispatchesRealZombieMinerBeyondLocalScan(
            GameTestHelper helper) {
        var core = new BlockPos(0,1,2);
        var remote = new BlockPos(7,1,2);
        helper.setBlock(core,SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(remote,Blocks.OAK_LOG);
        var be=helper.getLevel().getBlockEntity(helper.absolutePos(core));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Missing remote mining nest"); return;
        }
        Zombie worker=helper.spawn(EntityType.ZOMBIE,new BlockPos(0,1,0));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(core).asLong());
        boolean master=SwarmConfig.ENABLED.get();
        boolean lifecycle=SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean haul=SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean gather=SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get();
        boolean grief=helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true,helper.getLevel().getServer());
            var site=helper.absolutePos(remote);
            var home=helper.absolutePos(core);
            var pos=new SwarmNestOpportunityBoard.Position(
                    site.getX(),site.getY(),site.getZ());
            var homePos=new SwarmNestOpportunityBoard.Position(
                    home.getX(),home.getY(),home.getZ());
            if (!nest.opportunityBoard().publishBlock(pos,
                    SwarmNestColonyPolicy.Kind.TIMBER,helper.getLevel().getGameTime(),homePos)) {
                helper.fail("Cannot publish verified remote log");return;
            }
            var goal=new SwarmZombieColonyGatherGoal(worker);
            if(!goal.canUse() || nest.opportunityBoard().activeWorkers(
                    helper.getLevel().getGameTime())!=1) {
                helper.fail("Miner did not reserve the distant resource report");return;
            }
            goal.start();
            // Keep the destination loaded and move the test actor, not cargo;
            // this checks source revalidation and whole-item conservation,
            // not an end-to-end natural pathfinding guarantee.
            Vec3 there=helper.absoluteVec(new Vec3(6.5,1.0,2.5));
            worker.setPos(there.x,there.y,there.z);
            goal.tick();
            goal.stop();
            var drops=helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,new AABB(site).inflate(2),
                    e -> e.isAlive() && e.getItem().is(Items.OAK_LOG));
            if (!helper.getLevel().getBlockState(site).isAir()
                    || drops.size()!=1
                    || nest.resources()!=0
                    || nest.opportunityBoard().size(helper.getLevel().getGameTime())!=0) {
                helper.fail("Remote log scout handoff did not produce exact physical loot");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(lifecycle);
            SwarmConfig.NEST_HAULING_ENABLED.set(haul);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(gather);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief,helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_remote_prey_job",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void distantSpiderLivestockReportRequiresRealPreyBeforeMelee(
            GameTestHelper helper) {
        var core=new BlockPos(0,1,2);
        var far=new BlockPos(12,1,2);
        helper.setBlock(core,SwarmNestBlocks.NEST_CORE.get());
        var be=helper.getLevel().getBlockEntity(helper.absolutePos(core));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("No remote hunt core");return;
        }
        Zombie hunter=helper.spawn(EntityType.ZOMBIE,new BlockPos(0,1,0));
        hunter.setNoGravity(true);
        hunter.getPersistentData().putLong("SwarmColonyNest",
                helper.absolutePos(core).asLong());
        Pig pig=helper.spawn(EntityType.PIG,far);
        pig.setNoGravity(true);
        pig.setHealth(1.0f);
        boolean master=SwarmConfig.ENABLED.get();
        boolean life=SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean haul=SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean hunt=SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.get();
        boolean grief=helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean loot=helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true,helper.getLevel().getServer());
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT)
                    .set(true,helper.getLevel().getServer());
            var home=helper.absolutePos(core);
            var pos=pig.blockPosition();
            assert pos!=null;
            if(!nest.opportunityBoard().publishAnimal(pig.getUUID(),
                    new SwarmNestOpportunityBoard.Position(pos.getX(),pos.getY(),pos.getZ()),
                    helper.getLevel().getGameTime(),
                    new SwarmNestOpportunityBoard.Position(home.getX(),home.getY(),home.getZ()))) {
                helper.fail("Cannot announce real animal from remote scout");return;
            }
            var goal=new SwarmZombieColonyHuntGoal(hunter);
            if(!goal.canUse()) {
                helper.fail("Hunter could not reserve out-of-range living prey report");return;
            }
            goal.start();
            Vec3 where=helper.absoluteVec(new Vec3(11.5,1.0,2.5));
            hunter.setPos(where.x,where.y,where.z);
            goal.tick();
            goal.stop();
            var meat=helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,new AABB(pig.blockPosition()).inflate(3),
                    e -> e.isAlive() && e.getItem().is(Items.PORKCHOP));
            if(pig.isAlive() || meat.isEmpty() || nest.resources()!=0
                    || nest.opportunityBoard().size(helper.getLevel().getGameTime())!=0) {
                helper.fail("Remote scout pursuit must kill only actual adult prey and drop meat");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(life);
            SwarmConfig.NEST_HAULING_ENABLED.set(haul);
            SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.set(hunt);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief,helper.getLevel().getServer());
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT)
                    .set(loot,helper.getLevel().getServer());
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(batch = "swarm_runtime_colony_renewable_crops",
            templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void harvestedCarrotsOnlyReplantBySpendingPhysicalCropDrop(
            GameTestHelper helper) {
        var core = new BlockPos(0,1,2);
        var plot = new BlockPos(4,1,2);
        helper.setBlock(core,SwarmNestBlocks.NEST_CORE.get());
        helper.setBlock(plot.below(),Blocks.FARMLAND);
        helper.setBlock(plot,Blocks.CARROTS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CarrotBlock.AGE,7));
        var be=helper.getLevel().getBlockEntity(helper.absolutePos(core));
        if (!(be instanceof SwarmNestBlockEntity nest)) {
            helper.fail("Missing nest for renewable agriculture");return;
        }
        Zombie worker=helper.spawn(EntityType.ZOMBIE,new BlockPos(3,1,2));
        worker.setNoGravity(true);
        worker.getPersistentData().putLong("SwarmColonyNest",helper.absolutePos(core).asLong());
        boolean master=SwarmConfig.ENABLED.get();
        boolean life=SwarmConfig.NEST_LIFECYCLE_ENABLED.get();
        boolean haul=SwarmConfig.NEST_HAULING_ENABLED.get();
        boolean mine=SwarmConfig.NEST_BLOCK_GATHER_ENABLED.get();
        boolean replant=SwarmConfig.NEST_CROP_REPLANT_ENABLED.get();
        boolean grief=helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        try {
            SwarmConfig.ENABLED.set(true);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(true);
            SwarmConfig.NEST_HAULING_ENABLED.set(true);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(true);
            SwarmConfig.NEST_CROP_REPLANT_ENABLED.set(true);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true,helper.getLevel().getServer());
            var goal=new SwarmZombieColonyGatherGoal(worker);
            if(!goal.canUse()){
                helper.fail("Worker did not accept mature carrot crop");return;
            }
            goal.start();
            goal.tick();
            goal.stop();
            var plant=helper.getLevel().getBlockState(helper.absolutePos(plot));
            var carrotDrops=helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,new AABB(helper.absolutePos(plot)).inflate(2),
                    e -> e.isAlive() && e.getItem().is(Items.CARROT));
            if(!plant.is(Blocks.CARROTS)
                    || plant.getValue(net.minecraft.world.level.block.CarrotBlock.AGE)!=0
                    || nest.resources()!=0 || nest.nutrientPoints()!=0
                    || nest.haulTrips()!=0 || carrotDrops.stream()
                            .anyMatch(e -> e.getItem().isEmpty())) {
                helper.fail("Replant requires genuine harvested carrot and cannot credit storage");
                return;
            }
            helper.succeed();
        } finally {
            SwarmConfig.ENABLED.set(master);
            SwarmConfig.NEST_LIFECYCLE_ENABLED.set(life);
            SwarmConfig.NEST_HAULING_ENABLED.set(haul);
            SwarmConfig.NEST_BLOCK_GATHER_ENABLED.set(mine);
            SwarmConfig.NEST_CROP_REPLANT_ENABLED.set(replant);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(grief,helper.getLevel().getServer());
        }
    }
}
