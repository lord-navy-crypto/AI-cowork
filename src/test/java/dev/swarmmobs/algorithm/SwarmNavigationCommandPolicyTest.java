package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static dev.swarmmobs.algorithm.SwarmNavigationCommandPolicy.Decision.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmNavigationCommandPolicyTest {
    private static SwarmNavigationCommandPolicy.Decision decide(
            boolean issued, boolean done, long now, long prior,
            double x, double y, double z, double speed
    ) {
        return SwarmNavigationCommandPolicy.evaluate(
                issued, done, now, prior, x, y, z, speed,
                10.0, 64.0, 20.0, 1.0
        );
    }

    @Test
    void initialCommandAlwaysIssuedEvenWhenNavigationIsDone() {
        assertEquals(INITIAL, decide(false, true, 100, Long.MIN_VALUE, 10, 64, 20, 1));
    }

    @Test
    void stableMovingTargetDoesNotRepeatPathCreationEveryThreeTicks() {
        assertEquals(SKIP, decide(true, false, 103, 100, 10, 64, 20, 1));
        assertEquals(SKIP, decide(true, false, 119, 100, 10, 64, 20, 1));
        assertEquals(REFRESH_ACTIVE, decide(true, false, 120, 100, 10, 64, 20, 1));
    }

    @Test
    void finishedNavigationRetriesWithoutPerTickPathThrash() {
        assertEquals(SKIP, decide(true, true, 107, 100, 10, 64, 20, 1));
        assertEquals(RETRY_DONE, decide(true, true, 108, 100, 10, 64, 20, 1));
    }

    @Test
    void significantTargetChangeBypassesWaitingInterval() {
        assertEquals(TARGET_CHANGED, decide(true, false, 101, 100, 10.4, 64, 20, 1));
        assertEquals(TARGET_CHANGED, decide(true, true, 101, 100, 10, 64.6, 20, 1));
    }

    @Test
    void subBlockJitterDoesNotForceExcessiveRepath() {
        assertEquals(SKIP, decide(true, false, 101, 100, 10.1, 64.1, 20.1, 1));
        assertEquals(SKIP, decide(true, false, 101, 100, 10.25, 64, 20, 1));
    }

    @Test
    void speedChangeCanForceImmediateRecommand() {
        assertEquals(SPEED_CHANGED, decide(true, false, 101, 100, 10, 64, 20, 1.1));
        assertEquals(SKIP, decide(true, false, 101, 100, 10, 64, 20, 1.01));
    }

    @Test
    void clockRewindStartsFreshPath() {
        assertEquals(INITIAL, decide(true, false, 50, 100, 10, 64, 20, 1));
    }
}
