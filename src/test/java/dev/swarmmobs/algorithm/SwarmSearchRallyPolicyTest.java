package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SwarmSearchRallyPolicyTest {
    private static final Vec2 LAST = new Vec2(0,0);
    private static final Vec2 SELF = new Vec2(-8,0);
    private static final Vec2 WAYPOINT = new Vec2(-5,3);
    private static final List<Vec2> TEAM = List.of(
            new Vec2(2,1), new Vec2(2,-1));

    @Test void distantMemberMovesSlightlyTowardSameTargetPack() {
        var r = SwarmSearchRallyPolicy.adjust(
                SELF, WAYPOINT, LAST, TEAM, true);
        assertTrue(r.regrouping());
        assertEquals(2,r.peerCount());
        assertTrue(r.destination().x() > WAYPOINT.x());
        assertEquals(WAYPOINT.z(),r.destination().z(),1e-9);
        assertTrue(r.destination().subtract(WAYPOINT).length()
                <= SwarmSearchRallyPolicy.MAX_STEERING+1e-8);
    }

    @Test void nearbyPackKeepsIndependentSearchSectors() {
        var r = SwarmSearchRallyPolicy.adjust(
                new Vec2(0,0), WAYPOINT, LAST, TEAM, true);
        assertFalse(r.regrouping());
        assertEquals(WAYPOINT,r.destination());
    }

    @Test void noSameTargetPeersNoSyntheticGroup() {
        for(var peers:List.of(List.<Vec2>of(),List.of(new Vec2(1,0)))) {
            var r = SwarmSearchRallyPolicy.adjust(
                    SELF,WAYPOINT,LAST,peers,true);
            assertFalse(r.regrouping());
            assertEquals(WAYPOINT,r.destination());
        }
    }

    @Test void alliesOutsideLastKnownAreaAreNotTrusted() {
        var r = SwarmSearchRallyPolicy.adjust(
                SELF,WAYPOINT,LAST,
                List.of(new Vec2(20,0),new Vec2(21,1)),true);
        assertFalse(r.regrouping());
        assertEquals(WAYPOINT,r.destination());
    }

    @Test void disabledOrInvalidInputsLeaveSearchUnaffected() {
        assertFalse(SwarmSearchRallyPolicy.adjust(
                SELF,WAYPOINT,LAST,TEAM,false).regrouping());
        assertFalse(SwarmSearchRallyPolicy.adjust(
                new Vec2(Double.NaN,0),WAYPOINT,LAST,TEAM,true).regrouping());
        assertFalse(SwarmSearchRallyPolicy.adjust(
                SELF,WAYPOINT,new Vec2(Double.POSITIVE_INFINITY,0),
                TEAM,true).regrouping());
    }

    @Test void rallyCorrectionIsBoundedForWidelySeparatedAgents() {
        var r = SwarmSearchRallyPolicy.adjust(
                new Vec2(-60,0), WAYPOINT, LAST, TEAM,true);
        assertTrue(r.regrouping());
        assertEquals(SwarmSearchRallyPolicy.MAX_STEERING,
                r.destination().subtract(WAYPOINT).length(),1e-9);
    }

    @Test void excludesInvalidOrFarPeersWithoutMovingSearchGoal() {
        var r = SwarmSearchRallyPolicy.adjust(
                SELF,WAYPOINT,LAST,
                List.of(new Vec2(Double.NaN,1),new Vec2(90,0),
                        new Vec2(2,1)),true);
        assertFalse(r.regrouping());
        assertEquals(WAYPOINT,r.destination());
    }
}
