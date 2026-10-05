package dev.swarmmobs.agent;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmAgentStateTest {

    @Test
    void relayedMemoryCanPreserveTimestampWhileChangingSourceFlag() {
        SwarmAgentState state = new SwarmAgentState();
        UUID target = UUID.randomUUID();

        state.rememberTarget(target, 100L, true);
        assertTrue(state.directObservation());

        state.rememberTarget(target, 100L, false);

        assertEquals(target, state.targetId());
        assertEquals(100L, state.lastTargetObservationTick());
        assertFalse(state.directObservation());
    }

    @Test
    void forgettingTargetAlsoClearsPlannedDestination() {
        SwarmAgentState state = new SwarmAgentState();
        UUID target = UUID.randomUUID();

        state.rememberTarget(target, 50L, true);
        state.updateLocalPlan(3, 1, SwarmRole.FLANK_LEFT, 4.0, 8.0, 0.3, 0.5);
        assertTrue(state.hasDestination());

        state.forgetTarget();

        assertNull(state.targetId());
        assertFalse(state.hasDestination());
        assertFalse(state.directObservation());
    }
}
