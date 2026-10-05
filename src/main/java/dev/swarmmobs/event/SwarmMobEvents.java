package dev.swarmmobs.event;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmCombatPlanner;
import dev.swarmmobs.algorithm.TargetRelayPolicy;
import dev.swarmmobs.algorithm.TargetRelayPolicy.TargetRecord;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
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
        if (gameTick < state.nextPlanTick()) {
            return;
        }

        int interval = SwarmConfig.PLAN_INTERVAL_TICKS.get();
        state.scheduleNextPlan(gameTick, interval, zombie.getId());

        List<Zombie> neighbors = findNeighbors(level, zombie);
        Player target = findTarget(level, zombie, neighbors, state, gameTick);

        if (target == null) {
            state.forgetTarget();
            state.clearLocalPlan(neighbors.size());
            return;
        }

        int slots = SwarmConfig.FORMATION_SLOTS.get();
        Vec3 look = target.getLookAngle();
        List<Vec2> neighborPositions = neighbors.stream()
                .map(entity -> new Vec2(entity.getX(), entity.getZ()))
                .toList();

        SwarmCombatPlanner.Plan plan = SwarmCombatPlanner.plan(
                zombie.getUUID(),
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
                neighbors.size(),
                plan.formationSlot(),
                plan.role(),
                plan.destination().x(),
                plan.destination().z()
        );

        // Shared target selection is the cooperation layer. Vanilla melee behavior remains
        // responsible for the final attack once a mob is close enough.
        zombie.setTarget(target);

    }

    private static List<Zombie> findNeighbors(ServerLevel level, Zombie self) {
        double radius = SwarmConfig.NEIGHBOR_RADIUS.get();
        List<Zombie> nearby = level.getEntitiesOfClass(
                Zombie.class,
                self.getBoundingBox().inflate(radius),
                candidate -> candidate != self && candidate.isAlive() && !candidate.isNoAi()
        );

        nearby.sort(Comparator.comparingDouble(self::distanceToSqr));
        int limit = Math.min(SwarmConfig.MAX_NEIGHBORS.get(), nearby.size());
        return new ArrayList<>(nearby.subList(0, limit));
    }

    private static Player findTarget(
            ServerLevel level,
            Zombie self,
            List<Zombie> neighbors,
            SwarmAgentState state,
            long gameTick
    ) {
        Player direct = findDirectObservation(level, self);
        if (direct != null) {
            state.rememberTarget(direct.getUUID(), gameTick, true);
            return direct;
        }

        if (self.getTarget() instanceof Player vanillaTarget && validTarget(vanillaTarget)) {
            state.rememberTarget(vanillaTarget.getUUID(), gameTick, true);
            return vanillaTarget;
        }

        int memoryTicks = SwarmConfig.TARGET_MEMORY_TICKS.get();
        List<TargetRecord> records = new ArrayList<>();

        if (state.targetId() != null) {
            records.add(new TargetRecord(state.targetId(), state.lastTargetObservationTick()));
        }

        for (Zombie neighbor : neighbors) {
            SwarmAgentState neighborState = neighbor.getData(SwarmAttachments.AGENT_STATE.get());
            if (neighborState.targetId() != null) {
                records.add(new TargetRecord(
                        neighborState.targetId(),
                        neighborState.lastTargetObservationTick()
                ));
            }
        }

        var selected = TargetRelayPolicy.selectFreshest(gameTick, memoryTicks, records);
        if (selected.isPresent()) {
            TargetRecord record = selected.get();
            Player shared = resolvePlayer(level, record.targetId());
            if (shared != null) {
                boolean newerThanLocal = state.targetId() == null
                        || !state.targetId().equals(record.targetId())
                        || record.observationTick() > state.lastTargetObservationTick();

                if (newerThanLocal) {
                    state.rememberTarget(record.targetId(), record.observationTick(), false);
                }

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
