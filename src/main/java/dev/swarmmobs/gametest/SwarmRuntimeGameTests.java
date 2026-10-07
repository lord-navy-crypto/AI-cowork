package dev.swarmmobs.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import dev.swarmmobs.SwarmMobs;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.agent.SwarmPlannerContext;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.goal.SwarmApproachGoal;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
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
    @GameTest(batch = "swarm_runtime_zombie_engineering_execution_lease", templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 160)
    public static void claimedLongBreakOutlivesEngineeringAdvertisementTtl(GameTestHelper helper) {
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
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.STONE.defaultBlockState());

        SwarmZombieEngineerGoal engineer = zombie.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(SwarmZombieEngineerGoal.class::isInstance)
                .map(SwarmZombieEngineerGoal.class::cast)
                .findFirst()
                .orElse(null);

        if (engineer == null || !engineer.canUse()) {
            server.setDifficulty(previousDifficulty, true);
            helper.fail("Long-break engineering fixture could not start");
            return;
        }

        engineer.start();

        int afterAdvertisementExpiry =
                SwarmConfig.ZOMBIE_ENGINEERING_TASK_TTL_TICKS.get() + 5;

        helper.runAfterDelay(afterAdvertisementExpiry, () -> {
            if (!engineer.canContinueToUse()) {
                server.setDifficulty(previousDifficulty, true);
                helper.fail("Claimed long break was aborted when advertisement TTL expired");
                return;
            }

            for (int i = 0; i < 170; i++) {
                engineer.tick();
            }
            engineer.stop();

            if (!helper.getLevel().getBlockState(obstacle).isAir()) {
                server.setDifficulty(previousDifficulty, true);
                helper.fail("Long-break engineering lease did not allow STONE removal");
                return;
            }

            server.setDifficulty(previousDifficulty, true);
            helper.succeed();
        });
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
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "swarm-test-player"),
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

        helper.runAfterDelay(24, () -> {
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


}
