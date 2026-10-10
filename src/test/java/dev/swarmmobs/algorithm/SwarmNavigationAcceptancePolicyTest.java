package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNavigationAcceptancePolicyTest {
    @Test void firstPathCommandMayBeAttemptedImmediately() {
        assertTrue(SwarmNavigationAcceptancePolicy.mayRetry(100,
                Long.MIN_VALUE, 0,64,0, 5,64,5));
    }

    @Test void sameRejectedDestinationDoesNotSpamExpensivePathCreation() {
        for (int now = 101; now < 112; now++) {
            assertFalse(SwarmNavigationAcceptancePolicy.mayRetry(now,
                    100, 5,64,5,5,64,5));
        }
        assertTrue(SwarmNavigationAcceptancePolicy.mayRetry(
                112,100,5,64,5,5,64,5));
    }

    @Test void changedDestinationCanBypassBackoffForUsefulNewPlan() {
        assertFalse(SwarmNavigationAcceptancePolicy.mayRetry(
                101,100,5,64,5,5.49,64,5));
        assertTrue(SwarmNavigationAcceptancePolicy.mayRetry(
                101,100,5,64,5,5.5,64,5));
        assertTrue(SwarmNavigationAcceptancePolicy.mayRetry(
                101,100,5,64,5,5,64.5,5));
    }

    @Test void targetClockResetOrNewWorldDoesNotRemainBlocked() {
        assertTrue(SwarmNavigationAcceptancePolicy.mayRetry(
                2,500,5,64,5,5,64,5));
    }

    @Test void invalidCommandsCannotClaimSuccessfulNavigation() {
        assertFalse(SwarmNavigationAcceptancePolicy.mayRetry(
                120,100,5,64,5,Double.NaN,64,5));
        assertFalse(SwarmNavigationAcceptancePolicy.mayRetry(
                120,100,5,64,5,5,Double.POSITIVE_INFINITY,5));
    }
}
