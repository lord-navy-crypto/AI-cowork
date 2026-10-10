package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SwarmPackDecongestionPolicyTest {
    private static final Vec2 SELF = new Vec2(0, 0);
    private static final Vec2 BASE = new Vec2(0, 5);
    private static final Vec2 FORWARD = new Vec2(0, 1);

    @Test void denseLocalGroupSplitsAwayFromOccupiedRightLane() {
        var choice = SwarmPackDecongestionPolicy.choose(
                SELF, BASE, FORWARD, List.of(
                        new Vec2(1, 1), new Vec2(-1, 1),
                        new Vec2(2.5, 5), new Vec2(2.3, 5.5)),
                SwarmRole.CHASER, SwarmAgentArchetype.ASSAULT, 0, 0, true);
        assertTrue(choice.active());
        // Forward north => right points east, which has the occupied lane.
        assertEquals(-1, choice.side());
        assertEquals(new Vec2(-2.5,5),
                SwarmPackDecongestionPolicy.waypoint(BASE,FORWARD,choice.side()));
    }

    @Test void equalCrowdingUsesStableSlotParity() {
        var peers = List.of(new Vec2(1,1),new Vec2(-1,1),new Vec2(0,1.5));
        var a = SwarmPackDecongestionPolicy.choose(
                SELF,BASE,FORWARD,peers,SwarmRole.CHASER,
                SwarmAgentArchetype.ASSAULT,0,0,true);
        var b = SwarmPackDecongestionPolicy.choose(
                SELF,BASE,FORWARD,peers,SwarmRole.CHASER,
                SwarmAgentArchetype.ASSAULT,1,0,true);
        assertEquals(1,a.side());
        assertEquals(-1,b.side());
    }

    @Test void absentCrowdAndOtherSpeciesPreserveOriginalDestination() {
        var peers = List.of(new Vec2(1,1),new Vec2(12,0),new Vec2(-10,0));
        var none = SwarmPackDecongestionPolicy.choose(
                SELF,BASE,FORWARD,peers,SwarmRole.CHASER,
                SwarmAgentArchetype.ASSAULT,0,0,true);
        assertFalse(none.active());
        var spider = SwarmPackDecongestionPolicy.choose(
                SELF,BASE,FORWARD,List.of(new Vec2(1,1),new Vec2(-1,1),new Vec2(0,1.5)),
                SwarmRole.FLANK_LEFT,SwarmAgentArchetype.FLANKER,0,0,true);
        assertFalse(spider.active());
        assertEquals(BASE,SwarmPackDecongestionPolicy.waypoint(BASE,FORWARD,0));
    }

    @Test void oldLaneKeepsItsSideUnlessOtherSideMuchClearer() {
        var peers = List.of(new Vec2(1,1),new Vec2(-1,1),new Vec2(2.5,5));
        var choose = SwarmPackDecongestionPolicy.choose(
                SELF,BASE,FORWARD,peers,SwarmRole.CHASER,
                SwarmAgentArchetype.ASSAULT,0,1,true);
        assertEquals(1,choose.side()); // one occupant isn't enough to flip
    }

    @Test void blockedPreferredLaneChangesToOnlyOpenLane() {
        var proposed = new SwarmPackDecongestionPolicy.Choice(1,true,0,2);
        var feasible = SwarmPackDecongestionPolicy.chooseFeasible(
                proposed,false,true);
        assertTrue(feasible.active());
        assertEquals(-1,feasible.side());
    }

    @Test void bothBlockedLanesReturnInactiveForOrdinaryFallback() {
        var proposed = new SwarmPackDecongestionPolicy.Choice(-1,true,0,0);
        var feasible = SwarmPackDecongestionPolicy.chooseFeasible(
                proposed,false,false);
        assertFalse(feasible.active());
        assertEquals(0,feasible.side());
    }

    @Test void usablePreferredLaneRemainsStableAndDoesNotInventReroute() {
        var proposed = new SwarmPackDecongestionPolicy.Choice(1,true,1,1);
        assertEquals(proposed,SwarmPackDecongestionPolicy.chooseFeasible(
                proposed,true,true));
        assertEquals(proposed,SwarmPackDecongestionPolicy.chooseFeasible(
                proposed,true,false));
        assertFalse(SwarmPackDecongestionPolicy.chooseFeasible(
                null,true,true).active());
    }

    @Test void falseOrInvalidInputsDoNotCreateWaypoint() {
        var peers = List.of(new Vec2(1,1),new Vec2(-1,1),new Vec2(0,1.5));
        assertFalse(SwarmPackDecongestionPolicy.choose(
                SELF,BASE,FORWARD,peers,SwarmRole.CHASER,
                SwarmAgentArchetype.ASSAULT,0,0,false).active());
        assertFalse(SwarmPackDecongestionPolicy.choose(
                SELF,BASE,new Vec2(0,0),peers,SwarmRole.CHASER,
                SwarmAgentArchetype.ASSAULT,0,0,true).active());
        assertEquals(BASE,SwarmPackDecongestionPolicy.waypoint(
                BASE,new Vec2(Double.NaN, 1),1));
    }
}
