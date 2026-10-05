package dev.swarmmobs.event;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.FormationSlotAllocator;
import dev.swarmmobs.algorithm.SwarmCombatPlanner;
import dev.swarmmobs.algorithm.SwarmCommunicationPolicy;
import dev.swarmmobs.algorithm.TargetRelayPolicy;
import dev.swarmmobs.algorithm.TargetRelayPolicy.TargetRecord;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.debug.SwarmDebugParticles;
import dev.swarmmobs.goal.SwarmApproachGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SwarmMobEvents {

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie) || event.getLevel().isClientSide()) {
            return;
        }

        // Priority 1 outranks the vanilla melee movement goal while the swarm member
        // is outside releaseToVanillaDistance. Inside that radius this goal yields.
        zombie.goalSelector.addGoal(1, new SwarmApproachGoal(zombie));
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!SwarmConfig.ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Zombie zombie)) {
            return;
        }
        if (!(zombie.level() instanceof ServerLevel level) || zombie.isNoAi() || !zombie.isAlive()) {
            return;
        }

        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        long gameTick = level.getGameTime();
        int interval = SwarmConfig.PLAN_INTERVAL_TICKS.get();

        if (!state.planningScheduleInitialized()) {
            state.initializePlanSchedule(gameTick, interval, zombie.getId());
        }

        if (gameTick < state.nextPlanTick()) {
            return;
        }

        state.scheduleNextPlan(gameTick, interval);

        List<Zombie> movementNeighbors = findMovementNeighbors(level, zombie);
        List<Zombie> communicationNeighbors = findCommunicationNeighbors(level, zombie);

        receiveNeighborMessages(
                zombie,
                communicationNeighbors,
                state,
                gameTick
        );

        Player target = findTarget(level, zombie, state, gameTick);

        if (target == null) {
            state.forgetTarget();
            state.clearLocalPlan(movementNeighbors.size());
            if (zombie.getTarget() instanceof Player) {
                zombie.setTarget(null);
            }
            return;
        }

        int slots = SwarmConfig.FORMATION_SLOTS.get();
        Vec3 look = target.getLookAngle();
        List<Vec2> neighborPositions = movementNeighbors.stream()
                .map(entity -> new Vec2(entity.getX(), entity.getZ()))
                .toList();

        int assignedSlot = FormationSlotAllocator.allocate(
                zombie.getUUID(),
                movementNeighbors.stream().map(Zombie::getUUID).toList(),
                slots
        );

        SwarmCombatPlanner.Plan plan = SwarmCombatPlanner.planForSlot(
                assignedSlot,
                new Vec2(zombie.getX(), zombie.getZ()),
                new Vec2(target.getX(), target.getZ()),
                new Vec2(look.x, look.z),
                neighborPositions,
                slots,
                SwarmConfig.FORMATION_RADIUS.get(),
                SwarmConfig.SEPARATION_RADIUS.get(),
                SwarmConfig.SEPARATION_WEIGHT.get(),
                SwarmConfig.COHESION_WEIGHT.get()
        );

        state.updateLocalPlan(
                movementNeighbors.size(),
                plan.formationSlot(),
                plan.role(),
                plan.destination().x(),
                plan.destination().z(),
                plan.separationMagnitude(),
                plan.cohesionMagnitude()
        );

        // Shared target selection is the cooperation layer. Vanilla melee behavior remains
        // responsible for the final attack once a mob is close enough.
        zombie.setTarget(target);

        // Lightweight server-side visualization for development. This intentionally
        // uses vanilla particles so v0.1 needs no client renderer or extra dependency.
        if (gameTick % 10L == 0L) {
            SwarmDebugParticles.render(level, zombie, communicationNeighbors);
        }
    }

    private static List<Zombie> findMovementNeighbors(ServerLevel level, Zombie self) {
        return findNearbyPeers(
                level,
                self,
                SwarmConfig.NEIGHBOR_RADIUS.get(),
                SwarmConfig.MAX_NEIGHBORS.get()
        );
    }

    private static List<Zombie> findCommunicationNeighbors(ServerLevel level, Zombie self) {
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

    private static List<Zombie> findNearbyPeers(
            ServerLevel level,
            Zombie self,
            double radius,
            int maxNeighbors
    ) {
        List<Zombie> nearby = level.getEntitiesOfClass(
                Zombie.class,
                self.getBoundingBox().inflate(radius),
                candidate -> candidate != self && candidate.isAlive() && !candidate.isNoAi()
        );

        nearby.sort(Comparator.comparingDouble(self::distanceToSqr));
        int limit = Math.min(Math.max(0, maxNeighbors), nearby.size());
        return new ArrayList<>(nearby.subList(0, limit));
    }

    private static void receiveNeighborMessages(
            Zombie receiver,
            List<Zombie> communicationNeighbors,
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

        for (Zombie sender : communicationNeighbors) {
            if (!SwarmCommunicationPolicy.withinRange(
                    receiver.distanceToSqr(sender),
                    SwarmConfig.COMMUNICATION_RADIUS.get()
            )) {
                continue;
            }

            SwarmAgentState senderState = sender.getData(SwarmAttachments.AGENT_STATE.get());
            UUID targetId = senderState.targetId();
            if (targetId == null) {
                continue;
            }

            long age = gameTick - senderState.lastTargetObservationTick();
            if (age < 0 || age > memoryTicks) {
                continue;
            }

            var message = SwarmCommunicationPolicy.maybeTransmit(
                    sender.getUUID(),
                    receiver.getUUID(),
                    targetId,
                    senderState.lastTargetObservationTick(),
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

    private static Player findTarget(
            ServerLevel level,
            Zombie self,
            SwarmAgentState state,
            long gameTick
    ) {
        int memoryTicks = SwarmConfig.TARGET_MEMORY_TICKS.get();
        List<TargetRecord> records = new ArrayList<>();

        // Always drain messages that have reached their delivery tick. Direct local
        // perception still takes precedence below, but a continuously observing agent
        // must not accumulate an artificial backlog of already-deliverable messages.
        for (var message : state.drainDeliverableTargetMessages(gameTick)) {
            records.add(new TargetRecord(
                    message.targetId(),
                    message.observationTick()
            ));
        }

        Player direct = findDirectObservation(level, self);
        if (direct != null) {
            state.rememberTarget(direct.getUUID(), gameTick, true);
            return direct;
        }

        if (state.targetId() != null) {
            records.add(new TargetRecord(state.targetId(), state.lastTargetObservationTick()));
        }

        var selected = TargetRelayPolicy.selectFreshest(gameTick, memoryTicks, records);
        if (selected.isPresent()) {
            TargetRecord record = selected.get();
            Player shared = resolvePlayer(level, record.targetId());
            if (shared != null) {
                // Any non-LOS path is memory/relay. Message delivery preserves the
                // original observation tick even when latency is non-zero.
                state.rememberTarget(record.targetId(), record.observationTick(), false);
                return shared;
            }
        }
        return null;
    }

    private static Player findDirectObservation(ServerLevel level, Zombie self) {
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
