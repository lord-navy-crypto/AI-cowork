package dev.swarmmobs.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import dev.swarmmobs.SwarmMobs;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.goal.SwarmApproachGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class SwarmRuntimeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private SwarmRuntimeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
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
    @GameTest(templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
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
    @GameTest(templateNamespace = SwarmMobs.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void visiblePlayerIsAcquiredThroughPerceptionLayer(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setNoGravity(true);

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(3.0, 1.0, 2.0));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        if (!helper.getLevel().addFreshEntity(player)) {
            helper.fail("Mock player could not be inserted into the GameTest ServerLevel");
            return;
        }

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

}
