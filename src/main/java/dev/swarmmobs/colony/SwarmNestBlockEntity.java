package dev.swarmmobs.colony;

import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.config.SwarmConfig;
import dev.swarmmobs.registry.SwarmNestBlockEntities;
import dev.swarmmobs.registry.SwarmNestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.level.block.Blocks;
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
    private static final int[] DZ = {0, 0, 2, -2, 2, 2, -2, -2};
    private int resources;
    private int chamberLevel;
    // Actual placed shell modules can lag virtual capacity if the visual
    // feature was disabled earlier or neighboring space became obstructed.
    private int visibleChamberLevel;
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
    private long hauledItems;
    private long haulTrips;
    // Counts physical berry items spawned by a real ripe bush harvest;
    // they remain in-world and are NOT credited to nest inventory yet.
    private long foragedBerries;
    // Only a nonpersistent hint board: no virtual cargo or chunk tickets.
    private final SwarmNestScoutBoard scoutBoard = new SwarmNestScoutBoard();
    private final SwarmNestOpportunityBoard opportunityBoard = new SwarmNestOpportunityBoard();
    private final SwarmColonyWorkBoard workBoard = new SwarmColonyWorkBoard();
    private final SwarmColonyLaborFeedback laborFeedback = new SwarmColonyLaborFeedback();
    // Sparse signals live with the loaded nest; no persisted global pheromone map.
    private final SwarmNestPheromoneField pheromoneField = new SwarmNestPheromoneField();
    private int leaderMarks;

    public SwarmNestBlockEntity(BlockPos pos, BlockState state) {
        super(SwarmNestBlockEntities.NEST.get(), pos, state);
    }

    public int resources() { return resources; }
    public int chamberLevel() { return chamberLevel; }
    public int visibleChamberLevel() { return visibleChamberLevel; }
    public int effectiveCapacity() {
        return SwarmNestArchitecturePolicy.effectiveCapacity(
                chamberLevel, SwarmConfig.NEST_MAX_POPULATION.get());
    }
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
    public long hauledItems() { return hauledItems; }
    public long haulTrips() { return haulTrips; }
    public long foragedBerries() { return foragedBerries; }
    public void recordForagedBerries(int actualSpawnedItems) {
        if (actualSpawnedItems > 0) {
            foragedBerries += actualSpawnedItems;
            setChanged();
        }
    }
    public SwarmNestScoutBoard scoutBoard() { return scoutBoard; }
    public SwarmNestOpportunityBoard opportunityBoard() { return opportunityBoard; }
    public SwarmColonyWorkBoard workBoard() { return workBoard; }
    public SwarmColonyLaborFeedback laborFeedback() { return laborFeedback; }
    public SwarmNestPheromoneField pheromones() { return pheromoneField; }

    public boolean markPheromone(BlockPos where, SwarmNestColonyPolicy.Kind kind, long tick) {
        if (!(level instanceof ServerLevel server) || where == null
                || !server.hasChunkAt(where)) return false;
        return pheromoneField.observe(
                new SwarmNestPheromoneField.Position(
                        worldPosition.getX(),worldPosition.getY(),worldPosition.getZ()),
                new SwarmNestPheromoneField.Position(where.getX(),where.getY(),where.getZ()),
                kind,tick);
    }

    public double pheromoneCost(BlockPos at, SwarmNestColonyPolicy.Kind kind, long tick) {
        if (at == null) return 1.0;
        return pheromoneField.costFactor(new SwarmNestPheromoneField.Position(
                at.getX(), at.getY(), at.getZ()), kind, tick);
    }

    public void reinforcePheromone(BlockPos at, SwarmNestColonyPolicy.Kind kind, long tick) {
        reinforcePheromone(at,kind,tick,1.0);
    }

    public void reinforcePheromone(BlockPos at, SwarmNestColonyPolicy.Kind kind,
                                   long tick, double depositionMultiplier) {
        if (at == null || !(level instanceof ServerLevel server)
                || !server.hasChunkAt(at)) return;
        pheromoneField.reinforce(
                new SwarmNestPheromoneField.Position(
                        worldPosition.getX(),worldPosition.getY(),worldPosition.getZ()),
                new SwarmNestPheromoneField.Position(at.getX(),at.getY(),at.getZ()),
                kind,tick,depositionMultiplier);
    }

    public void inhibitPheromone(BlockPos at, SwarmNestColonyPolicy.Kind kind, long tick) {
        if (at == null || !(level instanceof ServerLevel server)
                || !server.hasChunkAt(at)) return;
        pheromoneField.inhibit(
                new SwarmNestPheromoneField.Position(
                        worldPosition.getX(),worldPosition.getY(),worldPosition.getZ()),
                new SwarmNestPheromoneField.Position(at.getX(),at.getY(),at.getZ()),
                kind,tick);
    }

    /** Scouts report only real, currently loaded resource entities. */
    public boolean reportScoutItem(ItemEntity item, long now) {
        if (!(level instanceof ServerLevel server) || item == null
                || item.level() != server || !item.isAlive()
                || item.getItem().isEmpty() || !server.hasChunkAt(item.blockPosition())) {
            return false;
        }
        BlockPos site = item.blockPosition();
        return scoutBoard.publish(item.getUUID(),
                new SwarmNestScoutBoard.Position(site.getX(), site.getY(), site.getZ()),
                classify(item.getItem()), now,
                new SwarmNestScoutBoard.Position(
                        worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
    }
    public int leaderMarks() { return leaderMarks; }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        resources = Math.max(0, Math.min(SwarmNestColonyPolicy.MAX_STORED_RESOURCES,
                tag.getInt("Resources")));
        chamberLevel = Math.max(0, Math.min(SwarmNestArchitecturePolicy.MAX_CHAMBER_LEVEL,
                tag.getInt("ChamberLevel")));
        visibleChamberLevel = Math.max(0, Math.min(chamberLevel,
                tag.getInt("VisibleChamberLevel")));
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
        hauledItems = Math.max(0L, tag.getLong("HauledItems"));
        haulTrips = Math.max(0L, tag.getLong("HaulTrips"));
        foragedBerries = Math.max(0L, tag.getLong("ForagedBerries"));
        leaderMarks = Math.max(0, Math.min(3, tag.getInt("LeaderMarks")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Resources", resources);
        tag.putInt("ChamberLevel", chamberLevel);
        tag.putInt("VisibleChamberLevel", visibleChamberLevel);
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
        tag.putLong("HauledItems", hauledItems);
        tag.putLong("HaulTrips", haulTrips);
        tag.putLong("ForagedBerries", foragedBerries);
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

    /**
     * Server-authoritative and matter-conserving delivery of a real world
     * item entity. Reuse the exact intake path for autonomous workers and
     * passive intake; never allocate a free replacement item.
     */
    public int acceptDroppedItem(ItemEntity item, int maxItems) {
        if (item == null || !item.isAlive() || item.getItem().isEmpty()
                || maxItems <= 0 || level == null || level.isClientSide
                || item.level() != level) {
            return 0;
        }
        ItemStack stack = item.getItem();
        int accepted = deposit(classify(stack), Math.min(maxItems, stack.getCount()));
        if (accepted <= 0) return 0;
        stack.shrink(accepted);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack);
        }
        return accepted;
    }

    /**
     * Same atomic item intake used by ordinary Nest Core suction, but count
     * successful voluntary worker trips separately for scientific telemetry.
     */
    public int acceptHaulDelivery(ItemEntity item, int maxItems) {
        var cargoKind = item == null ? SwarmNestColonyPolicy.Kind.NONE
                : classify(item.getItem());
        int accepted = acceptDroppedItem(item, maxItems);
        if (accepted > 0) {
            haulTrips++;
            hauledItems += accepted;
            if (level instanceof ServerLevel server) {
                laborFeedback.succeeded(cargoKind, accepted, server.getGameTime());
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
        // Any edible vanilla or modded ItemStack is biological food. Broaden
        // non-edible harvest produce to cover seeds, grains and mushrooms.
        if (stack.has(DataComponents.FOOD)
                || stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS) || stack.is(Items.MELON_SEEDS)
                || stack.is(Items.PUMPKIN_SEEDS) || stack.is(Items.COCOA_BEANS)
                || stack.is(Items.NETHER_WART) || stack.is(Items.BROWN_MUSHROOM)
                || stack.is(Items.RED_MUSHROOM) || stack.is(Items.SUGAR_CANE)
                || stack.is(Items.EGG) || stack.is(Items.HONEYCOMB)
                || stack.is(Items.SWEET_BERRIES) || stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BONE)
                || stack.is(Items.SPIDER_EYE) || stack.is(Items.BEEF)
                || stack.is(Items.PORKCHOP) || stack.is(Items.CHICKEN)
                || stack.is(Items.MUTTON) || stack.is(Items.RABBIT)
                || stack.is(Items.COD) || stack.is(Items.SALMON)
                || stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.COOKED_CHICKEN) || stack.is(Items.COOKED_MUTTON)
                || stack.is(Items.COOKED_RABBIT) || stack.is(Items.COOKED_COD)
                || stack.is(Items.COOKED_SALMON)) {
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
        enrollNearbyWorkers(level, members);
        assignVisibleLeaders(members);

        // When enabled, each module gets two actual shell blocks. Verify
        // both positions BEFORE charging the same eight-soil/six-log bill
        // that already governs virtual chambers. Direct dirt and raw logs
        // require no crafting, stripping or implicit conversion to planks.
        // Obstructed sites defer
        // the whole upgrade; they never overwrite existing construction.
        // If an old colony has paid-for abstract rooms, visualize at most
        // one such room per 200-tick cycle without charging it twice.
        boolean visible = SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.get();
        if (visible && visibleChamberLevel < chamberLevel) {
            tryPlaceShellModule(level, visibleChamberLevel);
        } else if (SwarmNestArchitecturePolicy.canExtend(
                chamberLevel, SwarmConfig.NEST_MAX_POPULATION.get(),
                members.size(), soilPoints, timberPoints)) {
            boolean worldReady = !visible || tryPlaceShellModule(level, chamberLevel);
            if (worldReady) {
                soilPoints -= SwarmNestArchitecturePolicy.SOIL_COST;
                timberPoints -= SwarmNestArchitecturePolicy.TIMBER_COST;
                resources -= SwarmNestArchitecturePolicy.SOIL_COST
                        + SwarmNestArchitecturePolicy.TIMBER_COST;
                chamberLevel++;
                setChanged();
            }
        }

        int workerCount = 0, guardCount = 0, scoutCount = 0, reserveCount = 0;
        for (PathfinderMob member : members) {
            if (member instanceof Zombie) workerCount++;
            else if (member instanceof Skeleton) guardCount++;
            else if (member.getType() == EntityType.SPIDER) scoutCount++;
            else if (member.getType() == EntityType.CREEPER) reserveCount++;
        }
        var science = SwarmColonySciencePolicy.evaluate(
                workerCount, guardCount, scoutCount, reserveCount,
                effectiveCapacity(),
                nutrientPoints + legacyPoints,
                SwarmConfig.NEST_WORKER_TARGET_SHARE.get(),
                SwarmConfig.NEST_GUARD_TARGET_SHARE.get(),
                SwarmConfig.NEST_RESPONSE_THRESHOLD.get());
        recordPopulationSample(science.population());
        SwarmNestScienceTelemetry.record(level, worldPosition, this, science);

        boolean playerClose = level.players().stream().anyMatch(
                player -> player.distanceToSqr(
                        worldPosition.getX() + .5, worldPosition.getY() + .5,
                        worldPosition.getZ() + .5) <= 12.0 * 12.0);

        long tick = level.getGameTime();
        // Cheap resource/cooldown/population gates before reading eight
        // separate positions. Most inactive colonies do zero birth-site IO.
        boolean birthCouldRun = nutrientPoints + legacyPoints >= SwarmNestColonyPolicy.SPAWN_COST
                && members.size() < effectiveCapacity()
                && tick >= nextSpawnTick
                && !playerClose
                && level.getDifficulty() != Difficulty.PEACEFUL
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        BlockPos birthSite = birthCouldRun ? findSpawnSite(level) : null;
        if (!SwarmNestColonyPolicy.canSpawn(
                true,
                level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING),
                level.getDifficulty() != Difficulty.PEACEFUL,
                level.hasChunkAt(worldPosition),
                observed, playerClose,
                birthSite != null,
                nutrientPoints + legacyPoints, members.size(),
                effectiveCapacity(), tick, nextSpawnTick)) {
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
            // One successful admission is part of this same sampling cycle.
            // Update both the persisted trend and the live science readout.
            lastPopulation++;
            peakPopulation = Math.max(peakPopulation, lastPopulation);
            populationDelta++;
            meanPopulation += populationSamples <= 1 ? 1.0 : 0.25;
            var afterBirth = SwarmColonySciencePolicy.evaluate(
                    workerCount + (recruit == SwarmColonySciencePolicy.Job.WORKER ? 1 : 0),
                    guardCount + (recruit == SwarmColonySciencePolicy.Job.GUARD ? 1 : 0),
                    scoutCount + (recruit == SwarmColonySciencePolicy.Job.SCOUT ? 1 : 0),
                    reserveCount + (recruit == SwarmColonySciencePolicy.Job.RESERVE ? 1 : 0),
                    effectiveCapacity(),
                    nutrientPoints + legacyPoints,
                    SwarmConfig.NEST_WORKER_TARGET_SHARE.get(),
                    SwarmConfig.NEST_GUARD_TARGET_SHARE.get(),
                    SwarmConfig.NEST_RESPONSE_THRESHOLD.get());
            SwarmNestScienceTelemetry.record(level, worldPosition, this, afterBirth);
            setChanged();
        }
    }

    /**
     * Intentionally tiny world footprint: two pieces for one module, no
     * excavation, no replacing ANY placed block and no chunk tickets.
     * One attempt per colony sample. Owned shell at the lower tier is the
     * only valid foundation for upper-tier elements.
     */
    private boolean tryPlaceShellModule(ServerLevel level, int module) {
        if (!SwarmConfig.NEST_VISIBLE_EXPANSION_ENABLED.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || module != visibleChamberLevel) {
            return false;
        }
        // Keep this optional terrain modification away from people, even if
        // an already-paid abstract room is now being visualized.
        boolean playerNearby = level.players().stream().anyMatch(player ->
                player.isAlive() && player.distanceToSqr(
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 12.0 * 12.0);
        if (playerNearby) return false;

        var soilPiece = SwarmNestVisibleShellPolicy.piece(module, 0);
        var timberPiece = SwarmNestVisibleShellPolicy.piece(module, 1);
        BlockPos soilPos = worldPosition.offset(
                soilPiece.x(), soilPiece.y(), soilPiece.z());
        BlockPos timberPos = worldPosition.offset(
                timberPiece.x(), timberPiece.y(), timberPiece.z());
        if (!canPlaceShellPiece(level, soilPos, soilPiece.y())
                || !canPlaceShellPiece(level, timberPos, timberPiece.y())) {
            return false;
        }
        // Minecraft game rules and modded placement listeners may still
        // refuse a setBlock. In that case release ONLY our first new block.
        var soilState = Blocks.DIRT.defaultBlockState();
        var timberState = Blocks.OAK_LOG.defaultBlockState();
        if (!level.setBlockAndUpdate(soilPos, soilState)) return false;
        if (!level.setBlockAndUpdate(timberPos, timberState)) {
            if (level.getBlockState(soilPos).equals(soilState)) {
                level.setBlockAndUpdate(soilPos, Blocks.AIR.defaultBlockState());
            }
            return false;
        }
        visibleChamberLevel++;
        setChanged();
        return true;
    }

    private boolean canPlaceShellPiece(ServerLevel level, BlockPos pos, int tier) {
        if (!level.isInWorldBounds(pos)
                || !level.hasChunkAt(pos)
                || !level.hasChunkAt(pos.below())
                || !level.getBlockState(pos).isAir()
                || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        var supporting = level.getBlockState(pos.below());
        boolean validFoundation = tier == 0
                ? supporting.is(Blocks.DIRT) || supporting.is(Blocks.GRASS_BLOCK)
                        || supporting.is(Blocks.COARSE_DIRT)
                        || supporting.is(Blocks.ROOTED_DIRT)
                        || supporting.is(Blocks.PODZOL)
                        || supporting.is(Blocks.MUD)
                        || supporting.is(Blocks.STONE)
                : supporting.is(Blocks.DIRT)
                        || supporting.is(Blocks.OAK_LOG)
                        // Existing test worlds may already have legacy shells
                        // made from mud bricks / stripped oak. Keep them valid
                        // as SUPPORTS, but never construct new crafted blocks.
                        || supporting.is(Blocks.MUD_BRICKS)
                        || supporting.is(Blocks.STRIPPED_OAK_LOG);
        if (!validFoundation
                || !supporting.isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        return level.getEntities((net.minecraft.world.entity.Entity) null,
                new AABB(pos), entity -> entity.isAlive()).isEmpty();
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

    private void enrollNearbyWorkers(ServerLevel level, List<PathfinderMob> members) {
        // Colony residence is persistent entity NBT; no central hive search
        // and no new chunk tickets. Idle Spider scouts share the same home
        // convention as Zombie carriers. Existing valid homes must not be
        // stolen by a second core, but a demolished LOADED home can be replaced.
        String dimension = level.dimension().location().toString();
        for (PathfinderMob member : members) {
            if (!(member instanceof Zombie) && member.getType() != EntityType.SPIDER) {
                continue;
            }
            if (member.isNoAi() || member.getTarget() != null) continue;
            var data = member.getPersistentData();
            if (data.contains("SwarmColonyNest")) {
                boolean wrongDimension = data.contains("SwarmColonyDimension")
                        && !dimension.equals(data.getString("SwarmColonyDimension"));
                if (!wrongDimension) {
                    BlockPos previousHome = BlockPos.of(data.getLong("SwarmColonyNest"));
                    if (!level.hasChunkAt(previousHome)) {
                        continue; // never force-load to investigate an old home
                    }
                    if (level.getBlockEntity(previousHome) instanceof SwarmNestBlockEntity
                            && level.getBlockState(previousHome)
                                    .is(SwarmNestBlocks.NEST_CORE.get())) {
                        continue;
                    }
                }
            }
            data.putLong("SwarmColonyNest", worldPosition.asLong());
            data.putString("SwarmColonyDimension", dimension);
        }
    }

    private void absorbDroppedResources(ServerLevel level) {
        // Small, local pickup of EXISTING dropped items: no free materials,
        // terrain harvesting, animal targeting, or arbitrary inventory reads.
        AABB area = new AABB(worldPosition).inflate(RESOURCE_RADIUS);
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class, area, item -> item.isAlive() && !item.getItem().isEmpty()
        );
        for (ItemEntity item : drops) {
            // A carrier's reserved item is still a physical entity but must
            // not be vacuumed up before that worker completes its delivery.
            if (SwarmNestHaulLease.isClaimed(item, level.getGameTime())) continue;
            acceptDroppedItem(item, item.getItem().getCount());
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
