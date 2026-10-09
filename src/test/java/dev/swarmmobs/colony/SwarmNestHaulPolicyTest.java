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
    void abandonedJobsExpireAndUnavailableItemsStop() {
        assertTrue(SwarmNestHaulPolicy.canContinue(0L, true, true, true));
        assertTrue(SwarmNestHaulPolicy.canContinue(259L, true, true, true));
        assertFalse(SwarmNestHaulPolicy.canContinue(260L, true, true, true));
        assertFalse(SwarmNestHaulPolicy.canContinue(4L, false, true, true));
        assertFalse(SwarmNestHaulPolicy.canContinue(4L, true, false, true));
        assertFalse(SwarmNestHaulPolicy.canContinue(4L, true, true, false));
    }
}
