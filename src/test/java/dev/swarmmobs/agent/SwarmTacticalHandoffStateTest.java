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

    @Test void newTargetResetsOldCrowdWaypoints() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(1,1));
        state.acceptCrowdLane(-1,300);
        state.bindTacticalTarget(new UUID(1,2));
        assertEquals(0,state.crowdLaneSide());
        assertEquals(1,state.acceptCrowdLane(1,301));
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
