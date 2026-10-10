package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestHaulPolicyTest {
    @Test
    void pickupNeedsIdleWorkerLoadedNestAllowedDropAndBoundedStack() {
        assertTrue(SwarmNestHaulPolicy.eligible(true, true, true,
                true, false, 2, 16, 100));
        assertFalse(SwarmNestHaulPolicy.eligible(false, true, true,
                true, false, 2, 16, 100));
        assertFalse(SwarmNestHaulPolicy.eligible(true, false, true,
                true, false, 2, 16, 100));
        assertFalse(SwarmNestHaulPolicy.eligible(true, true, false,
                true, false, 2, 16, 100));
        assertFalse(SwarmNestHaulPolicy.eligible(true, true, true,
                false, false, 2, 16, 100));
        assertFalse(SwarmNestHaulPolicy.eligible(true, true, true,
                true, true, 2, 16, 100));
        assertFalse(SwarmNestHaulPolicy.eligible(true, true, true,
                true, false, 64, 16, 100));
        assertFalse(SwarmNestHaulPolicy.eligible(true, true, true,
                true, false, 1, 16, 0));
    }

    @Test
    void liveItemLeaseIsExclusiveAndBecomesClaimableAfterExpiry() {
        assertTrue(SwarmNestHaulPolicy.mayClaim("", 150L, "worker-a", 100L));
        assertTrue(SwarmNestHaulPolicy.mayClaim("worker-a", 150L, "worker-a", 100L));
        assertFalse(SwarmNestHaulPolicy.mayClaim("worker-a", 150L, "worker-b", 120L));
        assertTrue(SwarmNestHaulPolicy.mayClaim("worker-a", 150L, "worker-b", 151L));
    }

    @Test
    void materialPriorityFavorsMissingConstructionStockButKeepsDistanceCost() {
        var soil = SwarmNestColonyPolicy.Kind.SOIL;
        var food = SwarmNestColonyPolicy.Kind.NUTRIENT;
        double shortage = SwarmNestHaulPolicy.pickupScore(
                soil, 16.0, 0, 6, 12, 0, 0, 12);
        double stocked = SwarmNestHaulPolicy.pickupScore(
                soil, 16.0, 8, 6, 12, 0, 0, 12);
        assertTrue(shortage < stocked);
        assertEquals(16.0, stocked);
        assertTrue(SwarmNestHaulPolicy.pickupScore(
                soil, 100.0, 0, 6, 12, 0, 0, 12) > shortage);
        assertEquals(stocked, SwarmNestHaulPolicy.pickupScore(
                soil, 16.0, 0, 0, 0, 0, 7, 32));
        assertTrue(SwarmNestHaulPolicy.pickupScore(
                food, 16.0, 0, 0, 0, 0, 0, 12)
                < SwarmNestHaulPolicy.pickupScore(
                        food, 16.0, 0, 0, 0, 12, 0, 12));
        assertEquals(Double.POSITIVE_INFINITY, SwarmNestHaulPolicy.pickupScore(
                SwarmNestColonyPolicy.Kind.NONE, 4.0, 0, 0, 0, 0, 0, 12));
    }

    @Test
    void cannotClaimAnItemThatWillNotFitRemainingPointCapacity() {
        assertTrue(SwarmNestHaulPolicy.hasRoomFor(127, 2, SwarmNestColonyPolicy.Kind.SOIL));
        assertFalse(SwarmNestHaulPolicy.hasRoomFor(127, 2, SwarmNestColonyPolicy.Kind.TIMBER));
        assertFalse(SwarmNestHaulPolicy.hasRoomFor(125, 1, SwarmNestColonyPolicy.Kind.NUTRIENT));
        assertTrue(SwarmNestHaulPolicy.hasRoomFor(125, 1, SwarmNestColonyPolicy.Kind.TIMBER));
        assertFalse(SwarmNestHaulPolicy.hasRoomFor(0, 0, SwarmNestColonyPolicy.Kind.SOIL));
    }

    @Test
    void stalledRouteYieldsTaskButSmallProgressResetsClockOnlyWhenMeaningful() {
        assertTrue(SwarmNestHaulPolicy.progress(Double.POSITIVE_INFINITY, 25.0));
        assertFalse(SwarmNestHaulPolicy.progress(25.0, 24.7));
        assertTrue(SwarmNestHaulPolicy.progress(25.0, 24.0));
        assertFalse(SwarmNestHaulPolicy.progress(1.0, Double.NaN));
        assertFalse(SwarmNestHaulPolicy.stalled(199, 100));
        assertTrue(SwarmNestHaulPolicy.stalled(200, 100));
        assertFalse(SwarmNestHaulPolicy.stalled(80, 100));
    }

    @Test
    void abandonedJobsExpireAndUnavailableItemsStop() {
        assertTrue(SwarmNestHaulPolicy.canContinue(0L, true, true, true));
        assertTrue(SwarmNestHaulPolicy.canContinue(259L, true, true, true));
        assertFalse(SwarmNestHaulPolicy.canContinue(260L, true, true, true));
        assertFalse(SwarmNestHaulPolicy.canContinue(4L, false, true, true));
        assertFalse(SwarmNestHaulPolicy.canContinue(4L, true, false, true));
        assertFalse(SwarmNestHaulPolicy.canContinue(4L, true, true, false));
    }
}
