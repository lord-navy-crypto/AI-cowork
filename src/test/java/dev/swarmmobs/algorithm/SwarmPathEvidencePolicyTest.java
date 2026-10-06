package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmPathEvidencePolicyTest {

    @Test
    void exactReachabilityIsAlwaysAcceptedForExistingPath() {
        assertTrue(SwarmPathEvidencePolicy.acceptable(
                true, true, 50.0, 0.0
        ));
    }

    @Test
    void nullPathIsNeverAccepted() {
        assertFalse(SwarmPathEvidencePolicy.acceptable(
                false, true, 0.0, 2.0
        ));
    }

    @Test
    void nearMissWithinResidualToleranceIsAccepted() {
        assertTrue(SwarmPathEvidencePolicy.acceptable(
                true, false, 0.75, 1.25
        ));
    }

    @Test
    void distantNearMissIsRejected() {
        assertFalse(SwarmPathEvidencePolicy.acceptable(
                true, false, 2.0, 1.25
        ));
    }

    @Test
    void nonFiniteResidualIsRejected() {
        assertFalse(SwarmPathEvidencePolicy.acceptable(
                true, false, Double.POSITIVE_INFINITY, 2.0
        ));
    }
}
