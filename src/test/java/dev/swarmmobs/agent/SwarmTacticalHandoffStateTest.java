package dev.swarmmobs.agent;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SwarmTacticalHandoffStateTest {

    @Test void targetChangesResetTacticalPatternAndOldBowBlocker() {
        SwarmAgentState state = new SwarmAgentState();
        UUID alpha = new UUID(0, 11);
        UUID beta = new UUID(0, 12);
        state.bindTacticalTarget(alpha);
        state.setTacticalPattern("SWEEP");
        state.setBowLaneClear(false);

        assertEquals("SWEEP", state.tacticalPattern());
        assertFalse(state.bowLaneClear());
        assertFalse(state.bindTacticalTarget(alpha));
        assertFalse(state.bowLaneClear());

        assertTrue(state.bindTacticalTarget(beta));
        assertEquals("STANDARD", state.tacticalPattern());
        assertTrue(state.bowLaneClear());
    }

    @Test void crowdSideOnlySwitchesAfterMinimumHold() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(1,1));
        assertEquals(1,state.acceptCrowdLane(1,100));
        assertEquals(1,state.acceptCrowdLane(-1,107));
        assertEquals(1,state.acceptCrowdLane(-1,119));
        assertEquals(-1,state.acceptCrowdLane(-1,120));
        assertEquals(4,state.crowdLaneUses());
        assertEquals(0,state.acceptCrowdLane(0,121));
    }

    @Test void blockedOldRouteMaySwitchImmediatelyWithoutOscillation() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(2,1));
        assertEquals(1,state.acceptCrowdLane(1,100));
        // Ordinary density updates cannot thrash an active lane.
        assertEquals(1,state.acceptCrowdLane(-1,102));
        // An impassable actual route is a reason to override hysteresis.
        assertEquals(-1,state.acceptCrowdLane(-1,103,true));
        assertEquals(-1,state.acceptCrowdLane(1,104));
        state.recordCrowdLaneRejected();
        assertEquals(1,state.crowdLaneRejected());
        assertEquals(0,state.acceptCrowdLane(0,105));
        assertEquals(1,state.acceptCrowdLane(1,106));
    }

    @Test void newTargetResetsOldCrowdWaypoints() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(1,1));
        state.acceptCrowdLane(-1,300);
        state.bindTacticalTarget(new UUID(1,2));
        assertEquals(0,state.crowdLaneSide());
        assertEquals(1,state.acceptCrowdLane(1,301));
    }

    @Test void searchRallyCountsEpisodesNotPlannerTicks() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(5,1));
        state.updateSearchRally(true);
        state.updateSearchRally(true);
        assertEquals(1,state.searchRallyEpisodes());
        assertTrue(state.searchRallyActive());
        state.updateSearchRally(false);
        state.updateSearchRally(true);
        assertEquals(2,state.searchRallyEpisodes());
    }

    @Test void targetChangeClearsSearchRally() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(5,1));
        state.updateSearchRally(true);
        state.bindTacticalTarget(new UUID(5,2));
        assertFalse(state.searchRallyActive());
        assertEquals(1,state.searchRallyEpisodes());
        state.forgetTarget();
        assertFalse(state.searchRallyActive());
    }

    @Test void clearingTargetDoesNotLeavePhantomSquadState() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(1, 2));
        state.setTacticalPattern("SURROUND");
        state.setBowLaneClear(false);
        state.bindTacticalTarget(null);
        assertEquals("STANDARD", state.tacticalPattern());
        assertTrue(state.bowLaneClear());
    }
}
