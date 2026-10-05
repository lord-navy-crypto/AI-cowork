package dev.swarmmobs.agent;

import dev.swarmmobs.algorithm.SwarmCommunicationPolicy.TargetMessage;
import dev.swarmmobs.algorithm.TargetObservation;
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
                new TargetMessage(sender, observation(target, 50L), 60L, 70L)
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

        TargetMessage first = new TargetMessage(sender, observation(target, 10L), 20L, 25L);
        TargetMessage duplicate = new TargetMessage(sender, observation(target, 10L), 21L, 26L);

        assertTrue(state.enqueueTargetMessage(first));
        assertFalse(state.enqueueTargetMessage(duplicate));
        assertEquals(1, state.pendingTargetMessageCount());
    }

    @Test
    void deliveredObservationIsNotAcceptedAgainFromSameSender() {
        SwarmAgentState state = new SwarmAgentState();
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        TargetMessage first = new TargetMessage(sender, observation(target, 40L), 50L, 55L);
        assertTrue(state.enqueueTargetMessage(first));
        assertEquals(1, state.drainDeliverableTargetMessages(55L).size());

        TargetMessage rebroadcast = new TargetMessage(sender, observation(target, 40L), 60L, 65L);
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
                new TargetMessage(sender, observation(target, 40L), 50L, 55L)
        ));
        state.drainDeliverableTargetMessages(55L);

        assertTrue(state.enqueueTargetMessage(
                new TargetMessage(sender, observation(target, 46L), 60L, 65L)
        ));
        assertEquals(1, state.pendingTargetMessageCount());
    }

    @Test
    void transientSlotCandidateDoesNotImmediatelyChangeRoleLane() {
        SwarmAgentState state = new SwarmAgentState();

        assertEquals(2, state.stabilizeFormationSlot(2, 100L, 20));
        assertEquals(2, state.stabilizeFormationSlot(5, 106L, 20));
        assertEquals(5, state.pendingFormationSlot());
        assertEquals(0L, state.formationSlotSwitchCount());

        assertEquals(2, state.stabilizeFormationSlot(2, 112L, 20));
        assertEquals(-1, state.pendingFormationSlot());
        assertEquals(0L, state.formationSlotSwitchCount());
    }

    @Test
    void persistentSlotCandidateSwitchesAfterHysteresisWindow() {
        SwarmAgentState state = new SwarmAgentState();

        assertEquals(1, state.stabilizeFormationSlot(1, 100L, 20));
        assertEquals(1, state.stabilizeFormationSlot(6, 110L, 20));
        assertEquals(1, state.stabilizeFormationSlot(6, 129L, 20));
        assertEquals(6, state.stabilizeFormationSlot(6, 130L, 20));
        assertEquals(1L, state.formationSlotSwitchCount());
        assertEquals(-1, state.pendingFormationSlot());
    }

    @Test
    void zeroHysteresisAllowsImmediateSlotSwitch() {
        SwarmAgentState state = new SwarmAgentState();

        assertEquals(0, state.stabilizeFormationSlot(0, 10L, 0));
        assertEquals(3, state.stabilizeFormationSlot(3, 11L, 0));
        assertEquals(1L, state.formationSlotSwitchCount());
    }


    @Test
    void targetConfidenceDecaysWithObservationAge() {
        SwarmAgentState state = new SwarmAgentState();
        UUID target = UUID.randomUUID();
        state.rememberTarget(observation(target, 100L), false);

        assertEquals(1.0, state.targetConfidence(100L, 100), 1.0e-9);
        assertEquals(0.5, state.targetConfidence(150L, 100), 1.0e-9);
        assertEquals(0.0, state.targetConfidence(200L, 100), 1.0e-9);
    }

    private static TargetObservation observation(UUID target, long tick) {
        return new TargetObservation(target, tick, 4.0, 64.0, 8.0, 0.0, 1.0);
    }
}
