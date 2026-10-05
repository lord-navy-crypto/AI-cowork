package dev.swarmmobs.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmStrategyDecisionTest {

    @Test
    void sanitizationClampsModelMultipliers() {
        SwarmStrategyDecision decision = new SwarmStrategyDecision(
                SwarmStrategyDecision.Mode.ENCIRCLE,
                9.0,
                -3.0,
                Double.NaN,
                "test",
                "ollama:test"
        ).sanitized();

        assertEquals(2.0, decision.formationRadiusMultiplier(), 1.0e-9);
        assertEquals(0.5, decision.separationMultiplier(), 1.0e-9);
        assertEquals(1.0, decision.cohesionMultiplier(), 1.0e-9);
    }

    @Test
    void baselineIsNeutral() {
        SwarmStrategyDecision baseline = SwarmStrategyDecision.baseline("deterministic", "fallback");

        assertEquals(SwarmStrategyDecision.Mode.BASELINE, baseline.mode());
        assertEquals(1.0, baseline.formationRadiusMultiplier(), 1.0e-9);
        assertEquals(1.0, baseline.separationMultiplier(), 1.0e-9);
        assertEquals(1.0, baseline.cohesionMultiplier(), 1.0e-9);
    }
}
