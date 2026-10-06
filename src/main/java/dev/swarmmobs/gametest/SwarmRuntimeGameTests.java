package dev.swarmmobs.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import dev.swarmmobs.SwarmMobs;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmBehaviorMode;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.goal.SwarmApproachGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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

            if ((spiderAState.role() != SwarmRole.FLANK_LEFT
                    && spiderAState.role() != SwarmRole.FLANK_RIGHT)
                    || (spiderBState.role() != SwarmRole.FLANK_LEFT
                    && spiderBState.role() != SwarmRole.FLANK_RIGHT)) {
                helper.fail("Dedicated Spider agents did not remain in flank roles");
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
