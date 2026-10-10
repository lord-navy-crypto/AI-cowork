package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmNestSurveyBudget;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmColonyEmergencePolicy;
import dev.swarmmobs.colony.SwarmNestPheromoneField;
import dev.swarmmobs.agent.SwarmTaskType;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.colony.SwarmNestHaulLease;
import dev.swarmmobs.colony.SwarmNestHaulPolicy;
import dev.swarmmobs.colony.SwarmNestScoutSignal;
import dev.swarmmobs.colony.SwarmNestScoutBoard;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.algorithm.SwarmEngagementPolicy;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Opt-in real-item worker logistics. Zombies do NOT craft, invent resources,
 * replace equipment or own a hidden virtual inventory. They escort the same
 * dropped ItemEntity from a reachable source to their loaded home Nest Core.
 * If interrupted, the physical item is left where the worker last held it.
 *
 * Search and navigation command creation are both throttled per dimension.
 * Vanilla close-range combat and high-priority Zombie engineering take over.
 */
public final class SwarmZombieColonyHaulGoal extends Goal {
    private enum Phase { NONE, TO_SCOUT, TO_ITEM, TO_NEST }

    private static final Map<ServerLevel, SwarmNestSurveyBudget> SEARCH_BUDGETS =
            new WeakHashMap<>();
    private static final Map<ServerLevel, SwarmNestSurveyBudget> MOVE_BUDGETS =
            new WeakHashMap<>();

    private final Zombie zombie;
    private ItemEntity item;
    // Physical route samples are transient and remain private to one trip.
    private final List<BlockPos> returnTrail = new ArrayList<>();
    private SwarmNestColonyPolicy.Kind carriedKind = SwarmNestColonyPolicy.Kind.NONE;
    private SwarmNestScoutBoard.Lead scoutLead;
    private BlockPos home;
    private Phase phase = Phase.NONE;
    private long nextSearchTick = Long.MIN_VALUE;
    private long lastMoveTick = Long.MIN_VALUE;
    private long startTick;
    private long lastProgressTick;
    private double bestRemainingDistanceSq = Double.POSITIVE_INFINITY;

    public SwarmZombieColonyHaulGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(zombie.level() instanceof ServerLevel level) || !idle(level)) {
            return false;
        }
        long tick = level.getGameTime();
        if (nextSearchTick != Long.MIN_VALUE && tick < nextSearchTick) {
            return false;
        }
        nextSearchTick = tick + SwarmConfig.NEST_HAUL_ATTEMPT_INTERVAL.get()
                + Math.floorMod(zombie.getId(), 23);

        var persistent = zombie.getPersistentData();
        if (!persistent.contains("SwarmColonyNest")
                || (persistent.contains("SwarmColonyDimension")
                        && !level.dimension().location().toString()
                                .equals(persistent.getString("SwarmColonyDimension")))) {
            return false;
        }
        BlockPos targetHome = BlockPos.of(persistent.getLong("SwarmColonyNest"));
        if (!level.hasChunkAt(targetHome)
                || !(level.getBlockEntity(targetHome) instanceof SwarmNestBlockEntity nest)
                || !level.getBlockState(targetHome).is(SwarmNestBlocks.NEST_CORE.get())
                || nest.resources() >= SwarmNestColonyPolicy.MAX_STORED_RESOURCES
                || zombie.distanceToSqr(targetHome.getX() + 0.5,
                        targetHome.getY() + 0.5, targetHome.getZ() + 0.5) > 24.0 * 24.0) {
            return false;
        }
        // One expensive item query per four ticks in the dimension,
        // regardless of how many zombies become idle at once.
        if (!SEARCH_BUDGETS.computeIfAbsent(level, ignored -> new SwarmNestSurveyBudget())
                .trySurvey(tick, 4)) {
            return false;
        }
        int radius = SwarmConfig.NEST_HAUL_SEARCH_RADIUS.get();
        // Reuse a bounded local peer snapshot; no second world scan per item.
        var nearbyPeers = level.getEntitiesOfClass(Zombie.class,
                zombie.getBoundingBox().inflate(radius + 2),
                other -> other != zombie && other.isAlive())
                .stream().limit(12).toList();
        String workerId = zombie.getUUID().toString();
        ItemEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (ItemEntity drop : level.getEntitiesOfClass(
                ItemEntity.class, zombie.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && !candidate.getItem().isEmpty())) {
            var kind = SwarmNestBlockEntity.classify(drop.getItem());
            if (!SwarmNestHaulPolicy.eligible(true, true, true,
                    kind != SwarmNestColonyPolicy.Kind.NONE,
                    playerNear(level, drop.getX(), drop.getY(), drop.getZ(), 6.0),
                    drop.getItem().getCount(), SwarmConfig.NEST_HAUL_MAX_STACK.get(),
                    SwarmNestColonyPolicy.MAX_STORED_RESOURCES - nest.resources())) {
                continue;
            }
            if (!SwarmNestHaulPolicy.hasRoomFor(
                    nest.resources(), drop.getItem().getCount(), kind)
                    || SwarmNestHaulLease.claimedByAnother(drop, workerId, tick)
                    || drop.distanceToSqr(targetHome.getX() + .5,
                            targetHome.getY() + .5, targetHome.getZ() + .5) < 16.0) {
                // Passive intake already owns drops very close to the core.
                continue;
            }
            // Nearby items still win unless the colony actually lacks a
            // category for construction or reproduction. Pure score, no
            // additional pathfinding queries and no spider-wide scans.
            double score = SwarmNestHaulPolicy.pickupScore(kind,
                    zombie.distanceToSqr(drop), nest.soilPoints(),
                    nest.timberPoints(), nest.nutrientPoints(), nest.legacyPoints(),
                    nest.chamberLevel(), SwarmConfig.NEST_MAX_POPULATION.get());
            // A roaming Spider can report an existing item, but the worker
            // still observes, claims, and moves that SAME physical entity.
            // Expired or foreign-nest hints have no effect on decisions.
            if (SwarmNestScoutSignal.recentFor(drop, targetHome, tick)) {
                score *= 0.75;
            }
            score = emergentCost(nest, kind, score, drop.blockPosition(),
                    nearbyPeers, tick);
            if (score < bestScore) {
                bestScore = score;
                best = drop;
            }
        }
        if (best != null) {
            if (!SwarmNestHaulLease.tryClaim(best, workerId, tick)) return false;
            // Direct sight of the actual item outranks a remote stale lead.
            // Remove its shared waypoint so another worker does not waste
            // navigation budget chasing cargo that is already claimed.
            nest.scoutBoard().discard(best.getUUID());
            item = best;
            carriedKind = SwarmNestBlockEntity.classify(best.getItem());
            scoutLead = null;
            home = targetHome;
            phase = Phase.TO_ITEM;
            return true;
        }
        // Direct sensing failed: use the nest's bounded Spider job board.
        // No distant ItemEntity lookup occurs here. The worker must actually
        // reach the reported coordinates before verifying the physical drop.
        var pos = zombie.blockPosition();
        var workerPos = new SwarmNestScoutBoard.Position(
                pos.getX(), pos.getY(), pos.getZ());
        var report = nest.scoutBoard().reserve(zombie.getUUID(),
                workerPos, tick, SwarmNestScoutBoard.MAX_DISTANCE,
                lead -> SwarmNestColonyPolicy.acceptAmount(
                        nest.resources(), 1, lead.kind()) > 0,
                lead -> emergentCost(nest, lead.kind(),
                        SwarmNestHaulPolicy.pickupScore(lead.kind(),
                                workerPos.distanceSquared(lead.position()),
                                nest.soilPoints(),nest.timberPoints(),
                                nest.nutrientPoints(),nest.legacyPoints(),
                                nest.chamberLevel(),SwarmConfig.NEST_MAX_POPULATION.get()),
                        scoutPosition(lead), nearbyPeers, tick));
        if (report == null || !level.hasChunkAt(scoutPosition(report))) {
            if (report != null) nest.scoutBoard().release(report.itemId(), zombie.getUUID());
            return false;
        }
        scoutLead = report;
        carriedKind = report.kind();
        item = null;
        home = targetHome;
        phase = Phase.TO_SCOUT;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(zombie.level() instanceof ServerLevel level)
                || !idle(level) || phase == Phase.NONE || home == null
                || !level.hasChunkAt(home)
                || !(level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest)
                || !level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get())) {
            return false;
        }
        boolean cargoValid = phase == Phase.TO_SCOUT
                ? scoutLead != null && level.hasChunkAt(scoutPosition(scoutLead))
                        && SwarmNestColonyPolicy.acceptAmount(nest.resources(), 1,
                                scoutLead.kind()) > 0
                : item != null && item.isAlive() && !item.getItem().isEmpty()
                        && SwarmNestHaulPolicy.hasRoomFor(nest.resources(),
                                item.getItem().getCount(),
                                SwarmNestBlockEntity.classify(item.getItem()));
        return SwarmNestHaulPolicy.canContinue(
                level.getGameTime() - startTick, true, cargoValid, true);
    }

    @Override
    public void start() {
        startTick = zombie.level().getGameTime();
        returnTrail.clear();
        lastMoveTick = Long.MIN_VALUE;
        lastProgressTick = startTick;
        bestRemainingDistanceSq = Double.POSITIVE_INFINITY;
    }

    @Override
    public void tick() {
        if (!(zombie.level() instanceof ServerLevel level) || home == null
                || (phase != Phase.TO_SCOUT
                        && (item == null || !item.isAlive()))) {
            phase = Phase.NONE;
            return;
        }
        long tick = level.getGameTime();
        String workerId = zombie.getUUID().toString();
        if (phase == Phase.TO_SCOUT) {
            if (scoutLead == null || !level.hasChunkAt(scoutPosition(scoutLead))
                    || !(level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest)
                    || !nest.scoutBoard().renew(
                            scoutLead.itemId(), zombie.getUUID(), tick)) {
                phase = Phase.NONE;
                return;
            }
            BlockPos where = scoutPosition(scoutLead);
            double x = where.getX() + .5, y = where.getY(), z = where.getZ() + .5;
            if (zombie.distanceToSqr(x, y, z) > 9.0) {
                follow(level, x, y, z, tick);
                return;
            }
            ItemEntity physical = level.getEntitiesOfClass(
                    ItemEntity.class, new AABB(where).inflate(3.0),
                    candidate -> candidate.isAlive()
                            && candidate.getUUID().equals(scoutLead.itemId()))
                    .stream().findFirst().orElse(null);
            if (physical == null) {
                // The observed item was despawned or moved. Never fabricate it.
                nest.scoutBoard().discard(scoutLead.itemId());
                if (SwarmConfig.NEST_PHEROMONES_ENABLED.get()) {
                    nest.inhibitPheromone(where, scoutLead.kind(), tick);
                }
                phase = Phase.NONE;
                return;
            }
            var kind = SwarmNestBlockEntity.classify(physical.getItem());
            if (kind != scoutLead.kind()
                    || !SwarmNestHaulPolicy.eligible(true, true, true,
                            kind != SwarmNestColonyPolicy.Kind.NONE,
                            playerNear(level, physical.getX(), physical.getY(),
                                    physical.getZ(), 6.0),
                            physical.getItem().getCount(),
                            SwarmConfig.NEST_HAUL_MAX_STACK.get(),
                            SwarmNestColonyPolicy.MAX_STORED_RESOURCES - nest.resources())
                    || !SwarmNestHaulPolicy.hasRoomFor(
                            nest.resources(), physical.getItem().getCount(), kind)
                    || !SwarmNestHaulLease.tryClaim(physical, workerId, tick)) {
                phase = Phase.NONE;
                return;
            }
            item = physical;
            carriedKind = kind;
            phase = Phase.TO_ITEM;
            lastProgressTick = tick;
            bestRemainingDistanceSq = Double.POSITIVE_INFINITY;
            zombie.getNavigation().stop();
        }
        if (tick % 20 == 0 && !SwarmNestHaulLease.tryClaim(item, workerId, tick)) {
            phase = Phase.NONE;
            return;
        }
        if (phase == Phase.TO_ITEM) {
            if (zombie.distanceToSqr(item) <= 2.25) {
                phase = Phase.TO_NEST;
                // Track progress independently on the return route.
                lastProgressTick = tick;
                bestRemainingDistanceSq = Double.POSITIVE_INFINITY;
                zombie.getNavigation().stop();
                rememberReturnPosition(item.blockPosition());
            } else {
                follow(level, item.getX(), item.getY(), item.getZ(), tick);
                return;
            }
        }
        if (phase == Phase.TO_NEST) {
            rememberReturnPosition(zombie.blockPosition());
            // Reject abnormal external relocation: the same item MUST still
            // be physically near the worker. It may not be deposited from
            // an unrelated location simply because the worker reached home.
            if (item.level() != level || zombie.distanceToSqr(item) > 16.0) {
                phase = Phase.NONE;
                return;
            }
            boolean atDock = zombie.distanceToSqr(
                    home.getX() + .5, home.getY() + .5, home.getZ() + .5) <= 5.0;
            // Drag the original ItemEntity, normally at most every three
            // ticks; always synchronize it at the dock before depositing.
            if (tick % 3 == 0 || atDock) {
                item.setDeltaMovement(Vec3.ZERO);
                item.setPos(zombie.getX(), zombie.getY() + .65, zombie.getZ());
            }
            if (atDock) {
                double cargoToDock = item.distanceToSqr(
                        home.getX() + .5, home.getY() + .5, home.getZ() + .5);
                if (cargoToDock <= 9.0
                        && level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest) {
                    int accepted = nest.acceptHaulDelivery(
                            item, SwarmConfig.NEST_HAUL_MAX_STACK.get());
                    if (accepted > 0) {
                        if (scoutLead != null) nest.scoutBoard().discard(scoutLead.itemId());
                        if (SwarmConfig.NEST_PHEROMONES_ENABLED.get()) {
                            // Crowding-sensitive deposition: successful routes
                            // still signal, but worker traffic limits excess
                            // positive feedback and prevents path lock-in.
                            int companions = level.getEntitiesOfClass(
                                    Zombie.class,zombie.getBoundingBox().inflate(4.0),
                                    other -> other != zombie && other.isAlive()).size();
                            double intensity = SwarmColonyEmergencePolicy
                                    .depositionMultiplier(companions);
                            for (BlockPos sampled : returnTrail) {
                                nest.reinforcePheromone(sampled, carriedKind,
                                        tick,intensity);
                            }
                        }
                    }
                }
                phase = Phase.NONE;
                zombie.getNavigation().stop();
                return;
            }
            follow(level, home.getX() + .5, home.getY(), home.getZ() + .5, tick);
        }
    }

    private void follow(ServerLevel level, double x, double y, double z, long tick) {
        double remaining = zombie.distanceToSqr(x, y, z);
        if (SwarmNestHaulPolicy.progress(bestRemainingDistanceSq, remaining)) {
            bestRemainingDistanceSq = remaining;
            lastProgressTick = tick;
        }
        if (SwarmNestHaulPolicy.stalled(tick, lastProgressTick)) {
            // Unreachable paths must not monopolize a lease until full task
            // expiry. Goal.stop releases the unconsumed physical item.
            phase = Phase.NONE;
            zombie.getNavigation().stop();
            return;
        }
        navigate(level, x, y, z, tick);
    }

    private void navigate(ServerLevel level, double x, double y, double z, long tick) {
        if (lastMoveTick != Long.MIN_VALUE
                && tick - lastMoveTick < SwarmNestHaulPolicy.RETRY_NAV_TICKS) {
            return;
        }
        if (!MOVE_BUDGETS.computeIfAbsent(level, ignored -> new SwarmNestSurveyBudget())
                .trySurvey(tick, 2)) return;
        lastMoveTick = tick;
        zombie.getNavigation().moveTo(x, y, z, 1.0);
    }

    private double emergentCost(SwarmNestBlockEntity nest,
                                SwarmNestColonyPolicy.Kind kind,
                                double baseCost, BlockPos at,
                                java.util.List<Zombie> peers, long tick) {
        double scent = 0, stop = 0;
        if (SwarmConfig.NEST_PHEROMONES_ENABLED.get()) {
            var p = new SwarmNestPheromoneField.Position(
                    at.getX(),at.getY(),at.getZ());
            scent = nest.pheromones().scent(
                    p,SwarmNestPheromoneField.signal(kind),tick);
            stop = nest.pheromones().scent(
                    p,SwarmNestPheromoneField.Signal.STOP,tick);
        }
        int crowd = (int) peers.stream().filter(peer ->
                peer.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5) < 9.0)
                .count();
        double experience = zombie.getData(SwarmAttachments.AGENT_STATE.get())
                .taskExperience(SwarmTaskType.MATERIAL);
        return SwarmColonyEmergencePolicy.workCost(
                baseCost * nest.laborFeedback().costFactor(kind,tick),
                zombie.getUUID(),kind,
                nest.soilPoints(),nest.timberPoints(),
                nest.nutrientPoints()+nest.legacyPoints(),
                SwarmColonyEmergencePolicy.sensedAttraction(
                        zombie.getUUID(),scent),stop,crowd,experience,
                nest.resourceDeficit(kind));
    }

    private void rememberReturnPosition(BlockPos pos) {
        if (pos == null || returnTrail.size() >= 16) return;
        if (returnTrail.isEmpty()
                || returnTrail.getLast().distSqr(pos) >= 9.0) {
            returnTrail.add(pos.immutable());
        }
    }

    @Override
    public void stop() {
        if (item != null) {
            // On interruption, leave a real item in the world, not a phantom
            // carried stack; an expired claim allows another worker to retry.
            SwarmNestHaulLease.release(item, zombie.getUUID().toString());
        }
        if (scoutLead != null && home != null
                && zombie.level() instanceof ServerLevel level
                && level.hasChunkAt(home)
                && level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest) {
            nest.scoutBoard().release(scoutLead.itemId(), zombie.getUUID());
        }
        if (phase != Phase.NONE) zombie.getNavigation().stop();
        item = null;
        returnTrail.clear();
        carriedKind = SwarmNestColonyPolicy.Kind.NONE;
        scoutLead = null;
        home = null;
        phase = Phase.NONE;
    }

    private static BlockPos scoutPosition(SwarmNestScoutBoard.Lead lead) {
        var p = lead.position();
        return new BlockPos(p.x(), p.y(), p.z());
    }

    private boolean idle(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get() || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                || !SwarmConfig.NEST_HAULING_ENABLED.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !zombie.isAlive() || zombie.isNoAi() || zombie.getTarget() != null) {
            return false;
        }
        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        return state.targetId() == null && !state.hasDestination()
                && SwarmEngagementPolicy.canDoNestWork(state.engagementMode());
    }

    private static boolean playerNear(ServerLevel level, double x, double y,
                                      double z, double radius) {
        return level.players().stream().anyMatch(player ->
                !player.isSpectator()
                        && player.distanceToSqr(x, y, z) <= radius * radius);
    }
}
