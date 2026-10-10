package dev.swarmmobs.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-only snapshot of the LAST locally active nest in each dimension.
 * Does not scan chunks, retain worlds in its values or force anything loaded.
 */
public final class SwarmNestScienceTelemetry {
    public record Snapshot(
            long sampleTick, int x, int y, int z, int population,
            int peakPopulation, int deltaPopulation, long samples,
            double averagePopulation, int chamberLevel, int visibleChamberLevel,
            int colonyCapacity, int soilPoints, int timberPoints,
            int nutrientPoints, int legacyPoints, int resourceTotal,
            long births, long hauledItems, long haulTrips, long foragedBerries,
            int activeWorkSites, int scoutItemLeads,
            long reinforcedTrips, long inhibitedJobs,
            double foodRecruitment, double timberRecruitment, double soilRecruitment,
            double foodInhibition, double timberInhibition, double soilInhibition,
            SwarmColonySciencePolicy.Sample science
    ) {
        public boolean available() { return sampleTick >= 0; }
    }

    private static final Map<ServerLevel, Snapshot> LAST = new WeakHashMap<>();
    private static final SwarmColonySciencePolicy.Sample EMPTY_MODEL =
            SwarmColonySciencePolicy.evaluate(0, 0, 0, 0, 1, 0, 0.4, 0.25, 0.55);
    private static final Snapshot EMPTY = new Snapshot(
            -1L, 0, 0, 0, 0, 0, 0, 0, 0.0,
            0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, EMPTY_MODEL);

    public static void record(ServerLevel level, BlockPos pos,
                              SwarmNestBlockEntity nest,
                              SwarmColonySciencePolicy.Sample model) {
        LAST.put(level, new Snapshot(
                level.getGameTime(), pos.getX(), pos.getY(), pos.getZ(),
                model.population(), nest.peakPopulation(), nest.populationDelta(),
                nest.populationSamples(), nest.meanPopulation(),
                nest.chamberLevel(), nest.visibleChamberLevel(), nest.effectiveCapacity(),
                nest.soilPoints(), nest.timberPoints(), nest.nutrientPoints(),
                nest.legacyPoints(), nest.resources(), nest.births(),
                nest.hauledItems(), nest.haulTrips(), nest.foragedBerries(),
                nest.workBoard().size(level.getGameTime()),
                nest.scoutBoard().size(level.getGameTime()),
                nest.laborFeedback().successes(), nest.laborFeedback().failures(),
                nest.laborFeedback().reinforcement(
                        SwarmNestColonyPolicy.Kind.NUTRIENT, level.getGameTime()),
                nest.laborFeedback().reinforcement(
                        SwarmNestColonyPolicy.Kind.TIMBER, level.getGameTime()),
                nest.laborFeedback().reinforcement(
                        SwarmNestColonyPolicy.Kind.SOIL, level.getGameTime()),
                nest.laborFeedback().inhibition(
                        SwarmNestColonyPolicy.Kind.NUTRIENT, level.getGameTime()),
                nest.laborFeedback().inhibition(
                        SwarmNestColonyPolicy.Kind.TIMBER, level.getGameTime()),
                nest.laborFeedback().inhibition(
                        SwarmNestColonyPolicy.Kind.SOIL, level.getGameTime()), model
        ));
    }

    public static Snapshot snapshot(ServerLevel level) {
        return LAST.getOrDefault(level, EMPTY);
    }

    private SwarmNestScienceTelemetry() {}
}
