package dev.swarmmobs.agent;

import dev.swarmmobs.algorithm.SwarmCommunicationPolicy.TargetMessage;
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
    @Test
    void initialPlanningStaggerDoesNotChangeSteadyStatePeriod() {
        SwarmAgentState state = new SwarmAgentState();

        state.initializePlanSchedule(100L, 6, 8); // floorMod(8, 6) = 2
        assertTrue(state.planningScheduleInitialized());
        assertEquals(102L, state.nextPlanTick());

        state.scheduleNextPlan(102L, 6);
        assertEquals(108L, state.nextPlanTick());

        state.scheduleNextPlan(108L, 6);
        assertEquals(114L, state.nextPlanTick());
    }

    @Test
    void differentEntityIdsOnlyChangeInitialPhase() {
        SwarmAgentState first = new SwarmAgentState();
        SwarmAgentState second = new SwarmAgentState();

        first.initializePlanSchedule(200L, 6, 1);
        second.initializePlanSchedule(200L, 6, 4);

        assertEquals(201L, first.nextPlanTick());
        assertEquals(204L, second.nextPlanTick());

        first.scheduleNextPlan(first.nextPlanTick(), 6);
        second.scheduleNextPlan(second.nextPlanTick(), 6);

        assertEquals(207L, first.nextPlanTick());
        assertEquals(210L, second.nextPlanTick());
        assertEquals(3L, second.nextPlanTick() - first.nextPlanTick());
    }
    @Test
    void delayedTargetMessagesStayQueuedUntilDeliveryTick() {
        SwarmAgentState state = new SwarmAgentState();
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertTrue(state.enqueueTargetMessage(
                new TargetMessage(sender, target, 50L, 60L, 70L)
        ));

        assertTrue(state.drainDeliverableTargetMessages(69L).isEmpty());
        assertEquals(1, state.pendingTargetMessageCount());

        var delivered = state.drainDeliverableTargetMessages(70L);
        assertEquals(1, delivered.size());
        assertEquals(target, delivered.getFirst().targetId());
        assertEquals(0, state.pendingTargetMessageCount());
        assertEquals(1L, state.communicationAcceptedMessages());
        assertEquals(1L, state.communicationDeliveredMessages());
    }

    @Test
    void identicalObservationFromSameSenderIsDeduplicated() {
        SwarmAgentState state = new SwarmAgentState();
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        TargetMessage first = new TargetMessage(sender, target, 10L, 20L, 25L);
        TargetMessage duplicate = new TargetMessage(sender, target, 10L, 21L, 26L);

        assertTrue(state.enqueueTargetMessage(first));
        assertFalse(state.enqueueTargetMessage(duplicate));
        assertEquals(1, state.pendingTargetMessageCount());
    }

    @Test
    void deliveredObservationIsNotAcceptedAgainFromSameSender() {
        SwarmAgentState state = new SwarmAgentState();
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        TargetMessage first = new TargetMessage(sender, target, 40L, 50L, 55L);
        assertTrue(state.enqueueTargetMessage(first));
        assertEquals(1, state.drainDeliverableTargetMessages(55L).size());

        TargetMessage rebroadcast = new TargetMessage(sender, target, 40L, 60L, 65L);
        assertFalse(state.enqueueTargetMessage(rebroadcast));
        assertEquals(0, state.pendingTargetMessageCount());
        assertEquals(1L, state.communicationAcceptedMessages());
        assertEquals(1L, state.communicationDeliveredMessages());
    }

    @Test
    void newerObservationFromSameSenderRemainsEligible() {
        SwarmAgentState state = new SwarmAgentState();
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertTrue(state.enqueueTargetMessage(
                new TargetMessage(sender, target, 40L, 50L, 55L)
        ));
        state.drainDeliverableTargetMessages(55L);

        assertTrue(state.enqueueTargetMessage(
                new TargetMessage(sender, target, 46L, 60L, 65L)
        ));
        assertEquals(1, state.pendingTargetMessageCount());
    }

}
