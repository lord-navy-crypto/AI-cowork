package dev.swarmmobs.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmAgentSensingTelemetryTest {

    @Test
    void sensingCountersAndLastNoiseAreObservable() {
        SwarmAgentState state = new SwarmAgentState();

        state.recordSensingAccepted(0.75);
        state.recordSensingAccepted(0.20);
        state.recordSensingDrop();

        assertEquals(2L, state.sensingAcceptedObservations());
        assertEquals(1L, state.sensingDroppedObservations());
        assertEquals(0.0, state.lastSensingNoiseMagnitude(), 1.0e-9);

        state.recordSensingAccepted(1.25);
        assertEquals(3L, state.sensingAcceptedObservations());
        assertEquals(1.25, state.lastSensingNoiseMagnitude(), 1.0e-9);
    }
}
