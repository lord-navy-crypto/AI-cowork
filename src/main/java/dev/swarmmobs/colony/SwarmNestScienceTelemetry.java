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
            double averagePopulation, int soilPoints, int timberPoints,
            int nutrientPoints, int legacyPoints, int resourceTotal,
            long births, SwarmColonySciencePolicy.Sample science
    ) {
        public boolean available() { return sampleTick >= 0; }
    }

    private static final Map<ServerLevel, Snapshot> LAST = new WeakHashMap<>();
    private static final SwarmColonySciencePolicy.Sample EMPTY_MODEL =
            SwarmColonySciencePolicy.evaluate(0, 0, 0, 0, 1, 0, 0.4, 0.25, 0.55);
    private static final Snapshot EMPTY = new Snapshot(
            -1L, 0, 0, 0, 0, 0, 0, 0, 0.0,
            0, 0, 0, 0, 0, 0, EMPTY_MODEL);

    public static void record(ServerLevel level, BlockPos pos,
                              SwarmNestBlockEntity nest,
                              SwarmColonySciencePolicy.Sample model) {
        LAST.put(level, new Snapshot(
                level.getGameTime(), pos.getX(), pos.getY(), pos.getZ(),
                model.population(), nest.peakPopulation(), nest.populationDelta(),
                nest.populationSamples(), nest.meanPopulation(),
                nest.soilPoints(), nest.timberPoints(), nest.nutrientPoints(),
                nest.legacyPoints(), nest.resources(), nest.births(), model
        ));
    }

    public static Snapshot snapshot(ServerLevel level) {
        return LAST.getOrDefault(level, EMPTY);
    }

    private SwarmNestScienceTelemetry() {}
}
