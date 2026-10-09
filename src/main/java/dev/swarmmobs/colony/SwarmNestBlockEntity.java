package dev.swarmmobs.colony;

import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.registry.SwarmNestBlockEntities;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A tiny resource economy, not a continuously simulated hive or a free mob
 * spawner. One core executes at most one local cycle per 200 game ticks.
 * No chunk tickets, terrain destruction, global scans, or asynchronous world IO.
 */
public final class SwarmNestBlockEntity extends BlockEntity {
    private static final int CYCLE_TICKS = 200;
    private static final int POPULATION_RADIUS = 14;
    private static final int RESOURCE_RADIUS = 3;
    private static final int[] DX = {2, -2, 0, 0, 2, -2, 2, -2};
    private static final int[] DZ = {0, 0, 2, -2, 2, 2, -2};
    private int resources;
    // Individually conserved resource categories. Legacy points keep old
    // 0.14 pre-science worlds compatible without inventing a food source.
    private int soilPoints;
    private int timberPoints;
    private int nutrientPoints;
    private int legacyPoints;
    private int lastPopulation;
    private int peakPopulation;
    private int populationDelta;
    private long populationSamples;
    private double meanPopulation;
    private long nextSpawnTick;
    private long births;
    private int leaderMarks;

    public SwarmNestBlockEntity(BlockPos pos, BlockState state) {
        super(SwarmNestBlockEntities.NEST.get(), pos, state);
    }

    public int resources() { return resources; }
    public int soilPoints() { return soilPoints; }
    public int timberPoints() { return timberPoints; }
    public int nutrientPoints() { return nutrientPoints; }
    public int legacyPoints() { return legacyPoints; }
    public int lastPopulation() { return lastPopulation; }
    public int peakPopulation() { return peakPopulation; }
    public int populationDelta() { return populationDelta; }
    public long populationSamples() { return populationSamples; }
    public double meanPopulation() { return meanPopulation; }
    public long births() { return births; }
    public int leaderMarks() { return leaderMarks; }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        resources = Math.max(0, Math.min(SwarmNestColonyPolicy.MAX_STORED_RESOURCES,
                tag.getInt("Resources")));
        // Clamp every resource bucket to the unallocated remainder. Old saves
        // have only "Resources": preserve them as explicitly labeled legacy
        // supply rather than pretending they are newly acquired nutrition.
        soilPoints = Math.max(0, Math.min(resources, tag.getInt("SoilPoints")));
        timberPoints = Math.max(0, Math.min(resources - soilPoints, tag.getInt("TimberPoints")));
        nutrientPoints = Math.max(0, Math.min(resources - soilPoints - timberPoints,
                tag.getInt("NutrientPoints")));
        legacyPoints = resources - soilPoints - timberPoints - nutrientPoints;
        lastPopulation = Math.max(0, tag.getInt("LastPopulation"));
        peakPopulation = Math.max(lastPopulation, tag.getInt("PeakPopulation"));
        populationDelta = tag.getInt("PopulationDelta");
        populationSamples = Math.max(0L, tag.getLong("PopulationSamples"));
        meanPopulation = Math.max(0.0, Math.min(64.0, tag.getDouble("MeanPopulation")));
        nextSpawnTick = Math.max(0L, tag.getLong("NextSpawnTick"));
        births = Math.max(0L, tag.getLong("Births"));
        leaderMarks = Math.max(0, Math.min(3, tag.getInt("LeaderMarks")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Resources", resources);
        tag.putInt("SoilPoints", soilPoints);
        tag.putInt("TimberPoints", timberPoints);
        tag.putInt("NutrientPoints", nutrientPoints);
        tag.putInt("LastPopulation", lastPopulation);
        tag.putInt("PeakPopulation", peakPopulation);
        tag.putInt("PopulationDelta", populationDelta);
        tag.putLong("PopulationSamples", populationSamples);
        tag.putDouble("MeanPopulation", meanPopulation);
        tag.putLong("NextSpawnTick", nextSpawnTick);
        tag.putLong("Births", births);
        tag.putInt("LeaderMarks", leaderMarks);
    }

    /** Also used by the runtime GameTest: bounded resource deposit. */
    public int deposit(SwarmNestColonyPolicy.Kind kind, int availableItems) {
        int accepted = SwarmNestColonyPolicy.acceptAmount(resources, availableItems, kind);
        if (accepted > 0) {
            int points = accepted * SwarmNestColonyPolicy.value(kind);
            resources += points;
            switch (kind) {
                case SOIL -> soilPoints += points;
                case TIMBER -> timberPoints += points;
                case NUTRIENT -> nutrientPoints += points;
                case NONE -> throw new IllegalStateException("NONE cannot be deposited");
            }
            setChanged();
        }
        return accepted;
    }

    public static SwarmNestColonyPolicy.Kind classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return SwarmNestColonyPolicy.Kind.NONE;
        if (stack.is(ItemTags.LOGS)) return SwarmNestColonyPolicy.Kind.TIMBER;
        if (stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT)
                || stack.is(Items.MUD) || stack.is(Items.GRAVEL)) {
            return SwarmNestColonyPolicy.Kind.SOIL;
        }
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BONE)
                || stack.is(Items.SPIDER_EYE) || stack.is(Items.BEEF)
                || stack.is(Items.PORKCHOP) || stack.is(Items.CHICKEN)) {
            return SwarmNestColonyPolicy.Kind.NUTRIENT;
        }
        return SwarmNestColonyPolicy.Kind.NONE;
    }

    public static void tick(Level world, BlockPos pos, BlockState state, SwarmNestBlockEntity core) {
        if (!(world instanceof ServerLevel level)
                || !SwarmConfig.ENABLED.get()
                || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()) return;

        // Arithmetic only for 199 out of 200 ticks, distributed by location.
        long tick = level.getGameTime();
        if (Math.floorMod(tick + pos.asLong(), CYCLE_TICKS) != 0) return;
        core.runColonyCycle(level);
    }

    public void runColonyCycle(ServerLevel level) {
        if (!SwarmConfig.ENABLED.get() || !SwarmConfig.NEST_LIFECYCLE_ENABLED.get()
                || level.getBlockEntity(worldPosition) != this
                || !level.getBlockState(worldPosition).is(SwarmNestBlocks.NEST_CORE.get())) {
            return;
        }
        // A player must be reasonably close to justify simulating this colony.
        // Nest activity never keeps a remote chunk loaded.
        boolean observed = level.players().stream().anyMatch(
                player -> !player.isSpectator() && player.distanceToSqr(
                        worldPosition.getX() + .5, worldPosition.getY() + .5,
                        worldPosition.getZ() + .5) <= 48.0 * 48.0);
        if (!observed) return;

        absorbDroppedResources(level);
        List<PathfinderMob> members = members(level);
        assignVisibleLeaders(members);

        int workerCount = 0, guardCount = 0, scoutCount = 0, reserveCount = 0;
        for (PathfinderMob member : members) {
            if (member instanceof Zombie) workerCount++;
            else if (member instanceof Skeleton) guardCount++;
            else if (member.getType() == EntityType.SPIDER) scoutCount++;
            else if (member.getType() == EntityType.CREEPER) reserveCount++;
        }
        var science = SwarmColonySciencePolicy.evaluate(
                workerCount, guardCount, scoutCount, reserveCount,
                SwarmConfig.NEST_MAX_POPULATION.get(),
                nutrientPoints + legacyPoints,
                SwarmConfig.NEST_WORKER_TARGET_SHARE.get(),
                SwarmConfig.NEST_GUARD_TARGET_SHARE.get(),
                SwarmConfig.NEST_RESPONSE_THRESHOLD.get());
        recordPopulationSample(science.population());
        SwarmNestScienceTelemetry.record(level, worldPosition, this, science);

        BlockPos birthSite = findSpawnSite(level);
        boolean playerClose = level.players().stream().anyMatch(
                player -> player.distanceToSqr(
                        worldPosition.getX() + .5, worldPosition.getY() + .5,
                        worldPosition.getZ() + .5) <= 12.0 * 12.0);

        long tick = level.getGameTime();
        if (!SwarmNestColonyPolicy.canSpawn(
                true,
                level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING),
                level.getDifficulty() != Difficulty.PEACEFUL,
                level.hasChunkAt(worldPosition),
                observed, playerClose,
                birthSite != null,
                nutrientPoints + legacyPoints, members.size(),
                SwarmConfig.NEST_MAX_POPULATION.get(), tick, nextSpawnTick)) {
            return;
        }

        // Recruitment follows local workforce deficits rather than an
        // imaginary queen command. Operators can restore simple rotation.
        var recruit = SwarmConfig.NEST_ADAPTIVE_RECRUITMENT.get()
                ? science.recommendedRecruit()
                : switch ((int) (births % 4L)) {
                    case 0 -> SwarmColonySciencePolicy.Job.WORKER;
                    case 1 -> SwarmColonySciencePolicy.Job.GUARD;
                    case 2 -> SwarmColonySciencePolicy.Job.SCOUT;
                    default -> SwarmColonySciencePolicy.Job.RESERVE;
                };
        EntityType<? extends Monster> chosen = switch (recruit) {
            case WORKER -> EntityType.ZOMBIE;
            case GUARD -> EntityType.SKELETON;
            case SCOUT -> EntityType.SPIDER;
            case RESERVE -> EntityType.CREEPER;
        };
        Monster child = chosen.create(level);
        if (child == null) return;
        child.moveTo(birthSite.getX() + .5, birthSite.getY(),
                birthSite.getZ() + .5, 0.0f, 0.0f);
        if (!level.noCollision(child)) {
            child.discard();
            return;
        }
        if (level.addFreshEntity(child)) {
            child.getPersistentData().putLong("SwarmColonyNest", worldPosition.asLong());
            // Reproduction consumes nutritional points first. Historical
            // unlabeled stock may fund older saves, but fresh dirt/timber may not.
            int nutrientSpent = Math.min(nutrientPoints, SwarmNestColonyPolicy.SPAWN_COST);
            nutrientPoints -= nutrientSpent;
            legacyPoints -= SwarmNestColonyPolicy.SPAWN_COST - nutrientSpent;
            resources -= SwarmNestColonyPolicy.SPAWN_COST;
            births++;
            nextSpawnTick = tick + SwarmNestColonyPolicy.SPAWN_COOLDOWN_TICKS;
            setChanged();
        }
    }

    private void recordPopulationSample(int actualCount) {
        int population = Math.max(0, actualCount);
        populationDelta = populationSamples == 0L ? 0 : population - lastPopulation;
        lastPopulation = population;
        peakPopulation = Math.max(peakPopulation, population);
        // A compact exponential mean is cheaper than retaining historical samples.
        meanPopulation = populationSamples == 0L
                ? population : 0.25 * population + 0.75 * meanPopulation;
        populationSamples++;
        setChanged();
    }

    private void absorbDroppedResources(ServerLevel level) {
        // Small, local pickup of EXISTING dropped items: no free materials,
        // terrain harvesting, animal targeting, or arbitrary inventory reads.
        AABB area = new AABB(worldPosition).inflate(RESOURCE_RADIUS);
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class, area, item -> item.isAlive() && !item.getItem().isEmpty()
        );
        for (ItemEntity item : drops) {
            SwarmNestColonyPolicy.Kind kind = classify(item.getItem());
            int accepted = deposit(kind, item.getItem().getCount());
            if (accepted == 0) continue;
            ItemStack remaining = item.getItem();
            remaining.shrink(accepted);
            if (remaining.isEmpty()) item.discard();
            else item.setItem(remaining);
        }
    }

    private List<PathfinderMob> members(ServerLevel level) {
        return level.getEntitiesOfClass(
                PathfinderMob.class,
                new AABB(worldPosition).inflate(POPULATION_RADIUS),
                mob -> mob.isAlive() && SwarmAgentProfiles.isSupported(mob)
        );
    }

    /**
     * "Regents" are symbolic, persistent colony roles for existing members:
     * never a forced new monarch mob. One Zombie and one Skeleton per nest.
     * Home tag and custom name are saved with the ordinary mob.
     */
    private void assignVisibleLeaders(List<PathfinderMob> members) {
        boolean zombiePresent = members.stream().anyMatch(m ->
                m instanceof Zombie && m.getPersistentData().getBoolean("SwarmColonyRegent")
                        && m.getPersistentData().getLong("SwarmColonyNest") == worldPosition.asLong());
        boolean skeletonPresent = members.stream().anyMatch(m ->
                m instanceof Skeleton && m.getPersistentData().getBoolean("SwarmColonyRegent")
                        && m.getPersistentData().getLong("SwarmColonyNest") == worldPosition.asLong());

        // Persistent marks stop dormant cores from continuously appointing
        // replacements whenever a regent walks outside the local range.
        if ((leaderMarks & 1) == 0 && !zombiePresent) {
            members.stream().filter(Zombie.class::isInstance).findFirst().ifPresent(mob -> {
                crown(mob, "entity.swarmmobs.nest_regent_zombie");
                leaderMarks |= 1;
                setChanged();
            });
        }
        if ((leaderMarks & 2) == 0 && !skeletonPresent) {
            members.stream().filter(Skeleton.class::isInstance).findFirst().ifPresent(mob -> {
                crown(mob, "entity.swarmmobs.nest_regent_skeleton");
                leaderMarks |= 2;
                setChanged();
            });
        }
    }

    private void crown(PathfinderMob mob, String translationKey) {
        mob.getPersistentData().putBoolean("SwarmColonyRegent", true);
        mob.getPersistentData().putLong("SwarmColonyNest", worldPosition.asLong());
        mob.setCustomName(Component.translatable(translationKey));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
    }

    private BlockPos findSpawnSite(ServerLevel level) {
        for (int i = 0; i < DX.length; i++) {
            BlockPos ground = worldPosition.offset(DX[i], -1, DZ[i]);
            BlockPos feet = ground.above();
            if (!level.hasChunkAt(ground) || !level.hasChunkAt(feet)
                    || !level.hasChunkAt(feet.above())) continue;
            if (level.getBlockState(feet).isAir()
                    && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)
                    && level.getFluidState(feet).isEmpty()
                    && level.getFluidState(ground).isEmpty()) {
                return feet;
            }
        }
        return null;
    }
}
