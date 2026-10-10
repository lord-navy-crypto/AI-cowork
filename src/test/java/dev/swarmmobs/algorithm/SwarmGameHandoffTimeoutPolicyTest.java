package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmGameHandoffTimeoutPolicyTest {
    @Test void optionalGamePositioningExpiresAtExactTickBudget() {
        assertFalse(SwarmGameHandoffTimeoutPolicy.timedOut(129,100,30));
        assertTrue(SwarmGameHandoffTimeoutPolicy.timedOut(130,100,30));
        assertFalse(SwarmGameHandoffTimeoutPolicy.timedOut(99,100,30));
    }

    @Test void incorrectOrUninitializedTimesNeverForceFakeTimeout() {
        assertFalse(SwarmGameHandoffTimeoutPolicy.timedOut(
                -1,0,30));
        assertFalse(SwarmGameHandoffTimeoutPolicy.timedOut(
                100,Long.MIN_VALUE,30));
        assertFalse(SwarmGameHandoffTimeoutPolicy.timedOut(
                100,50,-1));
    }

    @Test void cooldownNeverWrapsIntegerTickBounds() {
        assertEquals(180,SwarmGameHandoffTimeoutPolicy.nextEligibleTick(130,50));
        assertEquals(Long.MAX_VALUE,
                SwarmGameHandoffTimeoutPolicy.nextEligibleTick(Long.MAX_VALUE-4,50));
    }
}
