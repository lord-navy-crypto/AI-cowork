package dev.swarmmobs.event;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmCombatPlanner;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SwarmMobEvents {

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
            state.updateLocalPlan(neighbors.size(), 0, dev.swarmmobs.agent.SwarmRole.CHASER);
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

        state.updateLocalPlan(neighbors.size(), plan.formationSlot(), plan.role());

        // Shared target selection is the cooperation layer. Vanilla melee behavior remains
        // responsible for the final attack once a mob is close enough.
        zombie.setTarget(target);

        double releaseDistance = SwarmConfig.RELEASE_TO_VANILLA_DISTANCE.get();
        if (zombie.distanceToSqr(target) > releaseDistance * releaseDistance) {
            zombie.getNavigation().moveTo(
                    plan.destination().x(),
                    target.getY(),
                    plan.destination().z(),
                    SwarmConfig.MOVE_SPEED.get()
            );
        }
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

        UUID freshestTarget = null;
        long freshestTick = Long.MIN_VALUE;
        int memoryTicks = SwarmConfig.TARGET_MEMORY_TICKS.get();

        for (Zombie neighbor : neighbors) {
            SwarmAgentState neighborState = neighbor.getData(SwarmAttachments.AGENT_STATE.get());
            UUID candidate = neighborState.targetId();
            if (candidate == null) {
                continue;
            }

            long age = gameTick - neighborState.lastTargetObservationTick();
            if (age < 0 || age > memoryTicks) {
                continue;
            }

            if (neighborState.lastTargetObservationTick() > freshestTick) {
                freshestTick = neighborState.lastTargetObservationTick();
                freshestTarget = candidate;
            }
        }

        if (freshestTarget != null) {
            Player shared = resolvePlayer(level, freshestTarget);
            if (shared != null) {
                // Keep the original observation time. Relaying information should not make
                // old information look artificially fresh.
                state.rememberTarget(freshestTarget, freshestTick, false);
                return shared;
            }
        }

        UUID remembered = state.targetId();
        if (remembered != null) {
            long age = gameTick - state.lastTargetObservationTick();
            if (age >= 0 && age <= memoryTicks) {
                Player shared = resolvePlayer(level, remembered);
                if (shared != null) {
                    return shared;
                }
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
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player == null || player.level() != level || !validTarget(player)) {
            return null;
        }
        return player;
    }

    private static boolean validTarget(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private SwarmMobEvents() {}
}
