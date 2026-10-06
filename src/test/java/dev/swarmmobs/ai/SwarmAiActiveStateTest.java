package dev.swarmmobs.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmAiActiveStateTest {

    @AfterEach
    void clearState() {
        SwarmAiActiveState.clear();
    }

    @Test
    void acceptedStrategyExpiresBackToDeterministicMultipliers() {
        SwarmAiActiveState.consider(
                decision(SwarmStrategyDecision.Mode.ENCIRCLE, 1.2, 1.1, 0.9, 1.3),
                100L,
                40,
                10
        );

        var active = SwarmAiActiveState.snapshot(120L);
        assertTrue(active.active());
        assertEquals(1.2, active.formationRadiusMultiplier(), 1.0e-9);

        var expired = SwarmAiActiveState.snapshot(140L);
        assertFalse(expired.active());
        assertEquals(1.0, expired.formationRadiusMultiplier(), 1.0e-9);
        assertEquals(1.0, expired.separationMultiplier(), 1.0e-9);
        assertEquals(1.0, expired.cohesionMultiplier(), 1.0e-9);
        assertEquals(1.0, expired.searchRadiusMultiplier(), 1.0e-9);
    }

    @Test
    void differentModeWaitsUntilMinimumHoldCompletes() {
        SwarmAiActiveState.consider(
                decision(SwarmStrategyDecision.Mode.ENCIRCLE, 1.2, 1.0, 1.0, 1.0),
                100L,
                100,
                30
        );
        SwarmAiActiveState.consider(
                decision(SwarmStrategyDecision.Mode.CONCENTRATE, 0.8, 1.0, 1.2, 1.0),
                110L,
                100,
                30
        );

        var held = SwarmAiActiveState.snapshot(120L);
        assertEquals(SwarmStrategyDecision.Mode.ENCIRCLE, held.decision().mode());
        assertNotNull(held.pendingDecision());
        assertEquals(SwarmStrategyDecision.Mode.CONCENTRATE, held.pendingDecision().mode());

        var promoted = SwarmAiActiveState.snapshot(130L);
        assertEquals(SwarmStrategyDecision.Mode.CONCENTRATE, promoted.decision().mode());
        assertNull(promoted.pendingDecision());
    }

    @Test
    void sameModeMayRefreshWithinHoldWindow() {
        SwarmAiActiveState.consider(
                decision(SwarmStrategyDecision.Mode.ENCIRCLE, 1.1, 1.0, 1.0, 1.0),
                100L,
                40,
                30
        );
        SwarmAiActiveState.consider(
                decision(SwarmStrategyDecision.Mode.ENCIRCLE, 1.3, 1.0, 1.0, 1.0),
                110L,
                40,
                30
        );

        var active = SwarmAiActiveState.snapshot(120L);
        assertEquals(SwarmStrategyDecision.Mode.ENCIRCLE, active.decision().mode());
        assertEquals(1.3, active.formationRadiusMultiplier(), 1.0e-9);
        assertEquals(150L, active.expiresTick());
    }

    @Test
    void deterministicFallbackIsNeverActivated() {
        SwarmAiActiveState.consider(
                SwarmStrategyDecision.baseline(
                        "deterministic-fallback",
                        "provider unavailable"
                ),
                100L,
                100,
                20
        );

        assertFalse(SwarmAiActiveState.snapshot(100L).active());
    }

    private static SwarmStrategyDecision decision(
            SwarmStrategyDecision.Mode mode,
            double formation,
            double separation,
            double cohesion,
            double search
    ) {
        return new SwarmStrategyDecision(
                mode,
                formation,
                separation,
                cohesion,
                search,
                "test",
                "ollama-test"
        );
    }
}
