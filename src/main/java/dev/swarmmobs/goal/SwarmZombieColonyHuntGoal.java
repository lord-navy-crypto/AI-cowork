package dev.swarmmobs.goal;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.algorithm.SwarmNestSurveyBudget;
import dev.swarmmobs.colony.SwarmNestBlockEntity;
import dev.swarmmobs.colony.SwarmColonyGatherPolicy;
import dev.swarmmobs.colony.SwarmNestColonyPolicy;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Opt-in food procurement. Zombie attacks real adult farm animals using its
 * actual melee damage; all meat is created solely by the vanilla loot table.
 * Any world items must then be picked up by the separate hauling Goal.
 * A temporary victim lease avoids duplicate hunters pursuing one animal.
 */
public final class SwarmZombieColonyHuntGoal extends Goal {
    private static final int SEARCH_RADIUS = 10;
    private static final String CLAIM = "swarmmobs:colony_hunter";
    private static final String CLAIM_UNTIL = "swarmmobs:colony_hunt_until";
    private static final Map<ServerLevel, SwarmNestSurveyBudget> BUDGETS =
            new WeakHashMap<>();

    private final Zombie zombie;
    private Animal prey;
    private BlockPos home;
    private long nextSearch = Long.MIN_VALUE;
    private long started;
    private long lastProgress;
    private long lastMove = Long.MIN_VALUE;
    private long lastAttack = Long.MIN_VALUE;
    private double bestDistance;
    private boolean finished;

    public SwarmZombieColonyHuntGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(zombie.level() instanceof ServerLevel level) || !idle(level)) return false;
        long tick = level.getGameTime();
        if (nextSearch != Long.MIN_VALUE && tick < nextSearch) return false;
        nextSearch = tick + SwarmConfig.NEST_GATHER_INTERVAL.get()
                + Math.floorMod(zombie.getId(), 29);
        var data = zombie.getPersistentData();
        if (!data.contains("SwarmColonyNest")
                || (data.contains("SwarmColonyDimension")
                        && !level.dimension().location().toString()
                              .equals(data.getString("SwarmColonyDimension")))) return false;
        BlockPos candidateHome = BlockPos.of(data.getLong("SwarmColonyNest"));
        if (!level.hasChunkAt(candidateHome)
                || !level.getBlockState(candidateHome).is(SwarmNestBlocks.NEST_CORE.get())
                || !(level.getBlockEntity(candidateHome) instanceof SwarmNestBlockEntity nest)
                || zombie.distanceToSqr(candidateHome.getX() + .5,
                        candidateHome.getY() + .5, candidateHome.getZ() + .5)
                        > 24.0 * 24.0
                || !SwarmColonyGatherPolicy.needs(
                        SwarmNestColonyPolicy.Kind.NUTRIENT,
                        nest.soilPoints(), nest.timberPoints(),
                        nest.nutrientPoints() + nest.legacyPoints(), nest.resources()))
            return false;
        if (!BUDGETS.computeIfAbsent(level, ignored -> new SwarmNestSurveyBudget())
                .trySurvey(tick, 8)) return false;
        Animal best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Animal candidate : level.getEntitiesOfClass(
                Animal.class, zombie.getBoundingBox().inflate(SEARCH_RADIUS),
                animal -> validAnimal(animal))) {
            if (!level.hasChunkAt(candidate.blockPosition())
                    || claimedByOther(candidate, tick, zombie.getUUID().toString()))
                continue;
            // Make pigs and chickens the primary renewable food sources
            // without ignoring other farm animals when they are closer.
            double multiplier = candidate instanceof Pig || candidate instanceof Chicken
                    ? .75 : 1.0;
            double score = zombie.distanceToSqr(candidate) * multiplier;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null || !claim(best, tick)) return false;
        prey = best;
        home = candidateHome;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (finished || prey == null || !prey.isAlive()
                || !(zombie.level() instanceof ServerLevel level)
                || !idle(level) || home == null || !level.hasChunkAt(home)
                || !level.hasChunkAt(prey.blockPosition())
                || !level.getBlockState(home).is(SwarmNestBlocks.NEST_CORE.get())
                || !(level.getBlockEntity(home) instanceof SwarmNestBlockEntity nest)
                || !SwarmColonyGatherPolicy.needs(
                        SwarmNestColonyPolicy.Kind.NUTRIENT,
                        nest.soilPoints(), nest.timberPoints(),
                        nest.nutrientPoints() + nest.legacyPoints(), nest.resources())
                || !validAnimal(prey)) return false;
        return !SwarmColonyGatherPolicy.expired(
                level.getGameTime(), started, lastProgress)
                && zombie.distanceToSqr(prey) <= SEARCH_RADIUS * SEARCH_RADIUS * 4.0
                && !claimedByOther(prey, level.getGameTime(),
                        zombie.getUUID().toString());
    }

    @Override
    public void start() {
        started = zombie.level().getGameTime();
        lastProgress = started;
        lastMove = Long.MIN_VALUE;
        lastAttack = Long.MIN_VALUE;
        bestDistance = Double.POSITIVE_INFINITY;
        finished = false;
    }

    @Override
    public void tick() {
        if (!(zombie.level() instanceof ServerLevel level) || !canContinueToUse()) {
            finished = true;
            return;
        }
        long now = level.getGameTime();
        if (!claim(prey, now)) {
            finished = true;
            return;
        }
        double distance = zombie.distanceToSqr(prey);
        zombie.getLookControl().setLookAt(prey, 30.0f, 30.0f);
        if (distance > 4.0) {
            if (distance + .5 < bestDistance) {
                bestDistance = distance;
                lastProgress = now;
            }
            if (lastMove == Long.MIN_VALUE
                    || now - lastMove >= SwarmColonyGatherPolicy.RETRY_NAV_TICKS) {
                lastMove = now;
                zombie.getNavigation().moveTo(prey, 1.05);
            }
            return;
        }
        zombie.getNavigation().stop();
        if (lastAttack == Long.MIN_VALUE || now - lastAttack >= 20) {
            lastAttack = now;
            // Actual melee damage and standard vanilla animal loot, never
            // generated food added directly to the core or virtual storage.
            zombie.doHurtTarget(prey);
            if (!prey.isAlive()) finished = true;
        }
    }

    @Override
    public void stop() {
        if (prey != null) release(prey);
        zombie.getNavigation().stop();
        prey = null;
        home = null;
        finished = true;
    }

    private static boolean validAnimal(Animal a) {
        return a.isAlive() && !a.isBaby() && !a.hasCustomName()
                && (a instanceof Pig || a instanceof Chicken || a instanceof Cow
                    || a instanceof Sheep || a instanceof Rabbit);
    }

    private boolean claim(Animal animal, long tick) {
        if (claimedByOther(animal, tick, zombie.getUUID().toString())) return false;
        var data = animal.getPersistentData();
        data.putString(CLAIM, zombie.getUUID().toString());
        data.putLong(CLAIM_UNTIL, tick + 60);
        return true;
    }

    private static boolean claimedByOther(Animal animal, long now, String worker) {
        var tag = animal.getPersistentData();
        return tag.contains(CLAIM) && tag.getLong(CLAIM_UNTIL) >= now
                && !worker.equals(tag.getString(CLAIM));
    }

    private void release(Animal animal) {
        var data = animal.getPersistentData();
        if (zombie.getUUID().toString().equals(data.getString(CLAIM))) {
            data.remove(CLAIM);
            data.remove(CLAIM_UNTIL);
        }
    }

    private boolean idle(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get() || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                || !SwarmConfig.NEST_HAULING_ENABLED.get()
                || !SwarmConfig.NEST_ANIMAL_HUNT_ENABLED.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
                || !zombie.isAlive() || zombie.isNoAi()
                || zombie.getTarget() != null) return false;
        SwarmAgentState state = zombie.getData(SwarmAttachments.AGENT_STATE.get());
        return state.targetId() == null && !state.hasDestination();
    }
}
