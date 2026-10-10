package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmColonySciencePolicyTest {
    @Test
    void thresholdResponseIsMonotoneAndBounded() {
        assertEquals(0.0, SwarmColonySciencePolicy.response(0.0, 0.55), 1e-12);
        assertEquals(0.5, SwarmColonySciencePolicy.response(1.0, 1.0), 1e-12);
        assertTrue(SwarmColonySciencePolicy.response(2.0, 1.0)
                > SwarmColonySciencePolicy.response(1.0, 1.0));
        assertTrue(SwarmColonySciencePolicy.response(1.0, 2.0)
                < SwarmColonySciencePolicy.response(1.0, 1.0));
        assertTrue(SwarmColonySciencePolicy.response(100, 0.1) <= 1.0);
        assertTrue(Double.isFinite(SwarmColonySciencePolicy.response(Double.NaN, 0.55)));
    }

    @Test
    void initialColonyNeedsWorkerThenDiversifiesByLocalComposition() {
        var empty = sample(0, 0, 0, 0, 0);
        assertEquals(SwarmColonySciencePolicy.Job.WORKER, empty.recommendedRecruit());

        var workerOnly = sample(1, 0, 0, 0, 0);
        assertEquals(SwarmColonySciencePolicy.Job.GUARD, workerOnly.recommendedRecruit());

        var workerAndGuard = sample(1, 1, 0, 0, 0);
        assertEquals(SwarmColonySciencePolicy.Job.SCOUT, workerAndGuard.recommendedRecruit());
    }

    @Test
    void changingSharesChangesActualRecruitmentPriorities() {
        var guardHeavy = SwarmColonySciencePolicy.evaluate(
                1, 0, 0, 0, 12, 12, 0.15, 0.50, 0.55);
        assertEquals(SwarmColonySciencePolicy.Job.GUARD, guardHeavy.recommendedRecruit());
        var workerHeavy = SwarmColonySciencePolicy.evaluate(
                0, 1, 0, 0, 12, 12, 0.65, 0.10, 0.55);
        assertEquals(SwarmColonySciencePolicy.Job.WORKER, workerHeavy.recommendedRecruit());
    }

    @Test
    void populationAndNutritionReadinessAreMeasuredNotFabricated() {
        var sample = sample(2, 1, 1, 0, 6);
        assertEquals(4, sample.population());
        assertEquals(2, sample.workers());
        assertEquals(1, sample.guards());
        assertEquals(1, sample.scouts());
        assertEquals(4.0 / 12.0, sample.occupancy(), 1e-12);
        assertEquals(0.5, sample.nutritionReadiness(), 1e-12);
        assertEquals(1.0, sample(12, 0, 0, 0, 99).occupancy(), 1e-12);
        assertEquals(1.0, sample(0, 0, 0, 0, 20).nutritionReadiness(), 1e-12);
    }

    @Test
    void zeroPopulationAndUnusualInputsNeverProduceInvalidResponses() {
        var sample = SwarmColonySciencePolicy.evaluate(
                -1, -1, -1, -1, 0, -100, Double.NaN,
                Double.POSITIVE_INFINITY, -5.0);
        assertEquals(0, sample.population());
        assertEquals(1, sample.capacity());
        for (var job : SwarmColonySciencePolicy.Job.values()) {
            assertTrue(Double.isFinite(sample.response(job)));
            assertTrue(sample.response(job) >= 0.0 && sample.response(job) <= 1.0);
        }
    }

    private static SwarmColonySciencePolicy.Sample sample(
            int zombies, int skeletons, int spiders, int creepers, int nutrient
    ) {
        return SwarmColonySciencePolicy.evaluate(
                zombies, skeletons, spiders, creepers, 12, nutrient,
                0.40, 0.25, 0.55);
    }
}
