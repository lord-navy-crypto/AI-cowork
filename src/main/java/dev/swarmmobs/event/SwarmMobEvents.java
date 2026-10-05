package dev.swarmmobs.event;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.FormationSlotAllocator;
import dev.swarmmobs.algorithm.SwarmCombatPlanner;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.algorithm.SwarmCommunicationPolicy;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.algorithm.TargetRelayPolicy;
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

    private record TargetSelection(
            TargetObservation observation,
            Player player,
            boolean direct
    ) {}

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie) || event.getLevel().isClientSide()) {
            return;
        }

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

        TargetSelection selection = findTarget(level, zombie, state, gameTick);

        if (selection == null) {
            state.forgetTarget();
            state.clearLocalPlan(movementNeighbors.size());
            if (zombie.getTarget() instanceof Player) {
                zombie.setTarget(null);
            }
            return;
        }

        TargetObservation observation = selection.observation();
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

        Vec3 selfVelocity3 = zombie.getDeltaMovement();
        Vec2 selfVelocity = new Vec2(selfVelocity3.x, selfVelocity3.z);

        int candidateSlot = FormationSlotAllocator.allocate(
                zombie.getUUID(),
                movementNeighbors.stream().map(Zombie::getUUID).toList(),
                slots
        );

        int assignedSlot = state.stabilizeFormationSlot(
                candidateSlot,
                gameTick,
                SwarmConfig.FORMATION_SLOT_HYSTERESIS_TICKS.get()
        );

        SwarmCombatPlanner.Plan plan = SwarmCombatPlanner.planForSlotWithMotion(
                assignedSlot,
                new Vec2(zombie.getX(), zombie.getZ()),
                selfVelocity,
                new Vec2(observation.x(), observation.z()),
                new Vec2(observation.forwardX(), observation.forwardZ()),
                neighborPositions,
                neighborVelocities,
                slots,
                SwarmConfig.FORMATION_RADIUS.get(),
                SwarmConfig.SEPARATION_RADIUS.get(),
                SwarmConfig.SEPARATION_WEIGHT.get(),
                SwarmConfig.COHESION_WEIGHT.get(),
                SwarmConfig.ALIGNMENT_WEIGHT.get(),
                SwarmConfig.MAX_STEERING_CORRECTION.get()
        );

        state.updateLocalPlan(
                movementNeighbors.size(),
                plan.formationSlot(),
                plan.role(),
                plan.destination().x(),
                plan.destination().z(),
                plan.separationMagnitude(),
                plan.cohesionMagnitude(),
                plan.alignmentMagnitude(),
                plan.steeringMagnitude()
        );

        if (selection.direct()) {
            zombie.setTarget(selection.player());
        } else if (zombie.getTarget() instanceof Player) {
            // Do not allow vanilla AI to retain an exact live Player reference while
            // the swarm controller is operating from an indirect/stale observation.
            zombie.setTarget(null);
        }

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
            Zombie self,
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
            TargetObservation observation = new TargetObservation(
                    direct.getUUID(),
                    gameTick,
                    direct.getX(),
                    direct.getY(),
                    direct.getZ(),
                    look.x,
                    look.z
            );
            state.rememberTarget(observation, true);
            return new TargetSelection(observation, direct, true);
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
