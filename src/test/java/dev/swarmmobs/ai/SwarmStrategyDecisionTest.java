package dev.swarmmobs.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmStrategyDecisionTest {

    @Test
    void sanitizationClampsEveryMultiplier() {
        var decision = new SwarmStrategyDecision(
                SwarmStrategyDecision.Mode.ENCIRCLE,
                99.0,
                -5.0,
                Double.NaN,
                9.0,
                "bounded",
                "test"
        ).sanitized();

        assertEquals(1.50, decision.formationRadiusMultiplier(), 1.0e-9);
        assertEquals(0.70, decision.separationMultiplier(), 1.0e-9);
        assertEquals(1.00, decision.cohesionMultiplier(), 1.0e-9);
        assertEquals(1.40, decision.searchRadiusMultiplier(), 1.0e-9);
    }

    @Test
    void nullModeFallsBackToBaseline() {
        var decision = new SwarmStrategyDecision(
                null, 1.0, 1.0, 1.0, 1.0,
                null, null
        ).sanitized();

        assertEquals(SwarmStrategyDecision.Mode.BASELINE, decision.mode());
        assertEquals("", decision.rationale());
        assertEquals("unknown", decision.providerId());
    }

    @Test
    void rationaleIsBounded() {
        String longText = "x".repeat(500);
        var decision = new SwarmStrategyDecision(
                SwarmStrategyDecision.Mode.SEARCH,
                1.0, 1.0, 1.0, 1.0,
                longText, "test"
        ).sanitized();

        assertEquals(240, decision.rationale().length());
    }
}
