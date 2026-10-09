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
    void clearingLocalPlanAlsoClearsStaleNavigationAndPlannerTelemetry() {
        SwarmAgentState state = new SwarmAgentState();

        state.updateNavigationTelemetry(
                SwarmNavigationMode.OBSTACLE_DETOUR,
                3.0,
                4.0,
                true
        );
        state.updatePlannerTelemetry(
                SwarmPlannerContext.OBSTACLE_DETOUR,
                4,
                2,
                1,
                1,
                0,
                1.25,
                3L
        );

        state.clearLocalPlan(0);

        assertEquals(SwarmNavigationMode.PLAN, state.navigationMode());
        assertEquals(SwarmPlannerContext.NONE, state.plannerContext());
        assertEquals(0, state.plannerCandidateCount());
        assertEquals(0, state.plannerBlockedCount());
        assertEquals(0, state.plannerUnreachableCount());
        assertEquals(0, state.plannerFeasibleCount());
        assertFalse(state.hasNavigationWaypoint());
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
    void transientRoleCandidateDoesNotImmediatelyReassignResponsibility() {
        SwarmAgentState state = new SwarmAgentState();

        assertEquals(SwarmRole.CHASER, state.stabilizeRole(SwarmRole.CHASER, 100L, 12));
        assertEquals(SwarmRole.CHASER, state.stabilizeRole(SwarmRole.FLANK_LEFT, 106L, 12));
        assertEquals(SwarmRole.FLANK_LEFT, state.pendingRole());
        assertEquals(0L, state.roleReassignmentCount());

        assertEquals(SwarmRole.CHASER, state.stabilizeRole(SwarmRole.CHASER, 110L, 12));
        assertNull(state.pendingRole());
        assertEquals(0L, state.roleReassignmentCount());
    }

    @Test
    void persistentRoleCandidateReassignsAfterHysteresisWindow() {
        SwarmAgentState state = new SwarmAgentState();

        assertEquals(SwarmRole.CHASER, state.stabilizeRole(SwarmRole.CHASER, 100L, 12));
        assertEquals(SwarmRole.CHASER, state.stabilizeRole(SwarmRole.REAR_PRESSURE, 104L, 12));
        assertEquals(SwarmRole.CHASER, state.stabilizeRole(SwarmRole.REAR_PRESSURE, 115L, 12));
        assertEquals(SwarmRole.REAR_PRESSURE, state.stabilizeRole(SwarmRole.REAR_PRESSURE, 116L, 12));
        assertEquals(1L, state.roleReassignmentCount());
        assertNull(state.pendingRole());
    }

    @Test
    void zeroRoleHysteresisAllowsImmediateReassignment() {
        SwarmAgentState state = new SwarmAgentState();

        assertEquals(SwarmRole.CHASER, state.stabilizeRole(SwarmRole.CHASER, 10L, 0));
        assertEquals(SwarmRole.FLANK_RIGHT, state.stabilizeRole(SwarmRole.FLANK_RIGHT, 11L, 0));
        assertEquals(1L, state.roleReassignmentCount());
    }

    @Test
    void changingPlayerTargetImmediatelyReassignsSpiderFlankSide() {
        SwarmAgentState state = new SwarmAgentState();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        assertTrue(state.bindTacticalTarget(playerA));
        assertEquals(0, state.stabilizeFormationSlot(0, 100L, 40));
        assertEquals(SwarmRole.FLANK_LEFT,
                state.stabilizeRole(SwarmRole.FLANK_LEFT, 100L, 30));

        // On the same target, normal hysteresis prevents sudden side hopping.
        assertEquals(0, state.stabilizeFormationSlot(1, 105L, 40));
        assertEquals(SwarmRole.FLANK_LEFT,
                state.stabilizeRole(SwarmRole.FLANK_RIGHT, 105L, 30));

        assertTrue(state.bindTacticalTarget(playerB));
        assertEquals(playerB, state.tacticalAssignmentTarget());
        assertEquals(-1, state.pendingFormationSlot());
        assertNull(state.pendingRole());
        assertEquals(1, state.stabilizeFormationSlot(1, 106L, 40));
        assertEquals(SwarmRole.FLANK_RIGHT,
                state.stabilizeRole(SwarmRole.FLANK_RIGHT, 106L, 30));
    }

    @Test
    void switchingTargetsClearsPriorTemporaryWorkLeaseButPreservesExperience() {
        SwarmAgentState state = new SwarmAgentState();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        state.bindTacticalTarget(playerA);
        state.stabilizeSpecialization(
                SwarmTaskType.ENGINEERING, SwarmSpecialization.ENGINEER, 100L, 40);
        state.updateTaskExperience(SwarmTaskType.ENGINEERING, 0.3, 1.0);
        double retainedLearning = state.taskExperience(SwarmTaskType.ENGINEERING);
        assertEquals(SwarmSpecialization.ENGINEER, state.specialization());

        assertTrue(state.bindTacticalTarget(playerB));
        assertEquals(SwarmTaskType.RESERVE, state.currentTask());
        assertEquals(SwarmSpecialization.RESERVE, state.specialization());
        assertEquals(Long.MIN_VALUE, state.specializationSinceTick());
        assertEquals(retainedLearning, state.taskExperience(SwarmTaskType.ENGINEERING), 1.0e-9);

        // New target can immediately assign the correct task instead of
        // inheriting old target's 40-tick minimum specialization hold.
        assertEquals(SwarmSpecialization.FLANKER_LEFT,
                state.stabilizeSpecialization(
                        SwarmTaskType.FLANK, SwarmSpecialization.FLANKER_LEFT, 101L, 40));
    }

    @Test
    void sameTargetObservationRefreshNeverResetsHysteresis() {
        SwarmAgentState state = new SwarmAgentState();
        UUID playerA = UUID.randomUUID();
        assertTrue(state.bindTacticalTarget(playerA));
        assertEquals(0, state.stabilizeFormationSlot(0, 100L, 40));
        assertEquals(0, state.stabilizeFormationSlot(1, 101L, 40));
        assertFalse(state.bindTacticalTarget(playerA));
        assertEquals(0, state.stabilizeFormationSlot(1, 102L, 40));
        assertEquals(1, state.pendingFormationSlot());
    }

    @Test
    void forgettingTargetReleasesTacticalBindingAndStalePeerCounters() {
        SwarmAgentState state = new SwarmAgentState();
        UUID playerA = UUID.randomUUID();
        state.bindTacticalTarget(playerA);
        state.rememberTarget(playerA, 10L, true);
        state.updateTacticalSquadTelemetry(3, 1);
        state.forgetTarget();

        assertNull(state.tacticalAssignmentTarget());
        assertNull(state.targetId());
        assertEquals(0, state.tacticalPeerCount());
        assertEquals(0, state.tacticalBreacherCount());
        assertTrue(state.bindTacticalTarget(playerA));
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
