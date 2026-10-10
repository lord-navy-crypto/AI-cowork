package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmFourSpeciesRoutePolicyTest {
    private static final Vec2 SELF = new Vec2(0,0);
    private static final Vec2 WAYPOINT = new Vec2(5,0);
    private static final Vec2 TARGET = new Vec2(18,0);
    private static final Vec2 FORWARD = new Vec2(1,0);

    private static SwarmFourSpeciesRoutePolicy.Options propose(
            SwarmAgentArchetype type, List<Vec2> traffic) {
        return SwarmFourSpeciesRoutePolicy.propose(type, SELF, WAYPOINT,
                TARGET, FORWARD, traffic, 1, true, true);
    }

    @Test void zombieSpiderSkeletonAndCreeperCanAllSelectBetterMinecraftTrafficLane() {
        for (var type : SwarmAgentArchetype.values()) {
            var candidate = propose(type, List.of(WAYPOINT));
            assertTrue(candidate.eligible(), "type=" + type);
            assertEquals(1,candidate.originalOccupancy());
            assertEquals(0,candidate.leftOccupancy());
            assertEquals(0,candidate.rightOccupancy());
            var picked = SwarmFourSpeciesRoutePolicy.choose(candidate,
                    true,true,0,0);
            assertTrue(picked.changed(), "type=" + type);
            assertEquals(new Vec2(5,2.25),picked.destination());
            assertEquals(0,picked.finalOccupancy());
        }
    }

    @Test void neverChangeRouteWhenNoActualCongestionOrInvalidEvidence() {
        assertFalse(propose(SwarmAgentArchetype.FLANKER,List.of(
                new Vec2(-20,20))).eligible());
        assertFalse(SwarmFourSpeciesRoutePolicy.propose(
                SwarmAgentArchetype.ASSAULT, SELF,WAYPOINT,TARGET,FORWARD,
                List.of(WAYPOINT),0.2,true,true).eligible());
        assertFalse(SwarmFourSpeciesRoutePolicy.propose(
                SwarmAgentArchetype.BREACHER, SELF,WAYPOINT,TARGET,FORWARD,
                List.of(WAYPOINT),1,false,true).eligible());
        assertFalse(SwarmFourSpeciesRoutePolicy.propose(
                SwarmAgentArchetype.RANGED_SUPPORT, SELF,WAYPOINT,TARGET,FORWARD,
                List.of(WAYPOINT),1,true,false).eligible());
        assertFalse(SwarmFourSpeciesRoutePolicy.propose(
                SwarmAgentArchetype.FLANKER, SELF,WAYPOINT,TARGET,new Vec2(0,0),
                List.of(WAYPOINT),1,true,true).eligible());
    }

    @Test void preserveVanillaSpeciesCombatAtCloseGameDistances() {
        for (var type : SwarmAgentArchetype.values()) {
            var p = SwarmFourSpeciesRoutePolicy.propose(type,
                    new Vec2(15,0),WAYPOINT,TARGET,FORWARD,
                    List.of(WAYPOINT),1,true,true);
            assertFalse(p.eligible(), "near target "+type);
        }
    }

    @Test void noWorldSquareAvailableMeansNoDetourNoAttackOverride() {
        var options=propose(SwarmAgentArchetype.BREACHER,List.of(WAYPOINT));
        var picked=SwarmFourSpeciesRoutePolicy.choose(
                options,false,false,0,0);
        assertFalse(picked.changed());
        assertEquals(WAYPOINT,picked.destination());
        assertEquals(0,picked.side());
    }

    @Test void chooseClearerWorldVerifiedCandidateAndPreferPriorSideInTie() {
        var leftOnly=SwarmFourSpeciesRoutePolicy.choose(
                propose(SwarmAgentArchetype.FLANKER,List.of(WAYPOINT)),
                true,false,0,1);
        assertEquals(1,leftOnly.side());
        var rightOnly=SwarmFourSpeciesRoutePolicy.choose(
                propose(SwarmAgentArchetype.FLANKER,List.of(WAYPOINT)),
                false,true,0,0);
        assertEquals(-1,rightOnly.side());
        var held=SwarmFourSpeciesRoutePolicy.choose(
                propose(SwarmAgentArchetype.ASSAULT,List.of(WAYPOINT)),
                true,true,-1,0);
        assertEquals(-1,held.side());
    }

    @Test void choicesNeverPreferEqualOrMoreCrowdedPosition() {
        var o=propose(SwarmAgentArchetype.RANGED_SUPPORT,
                List.of(WAYPOINT, new Vec2(5,2.25)));
        assertEquals(2,o.originalOccupancy());
        assertEquals(1,o.leftOccupancy());
        assertEquals(1,o.rightOccupancy());
        var selected=SwarmFourSpeciesRoutePolicy.choose(o,true,true,0,0);
        assertTrue(selected.changed());
        assertTrue(selected.finalOccupancy() < selected.originalOccupancy());
    }

    @Test void rejectBadCoordinateAndNonFiniteGeometryWithoutInventingWaypoints() {
        var p=SwarmFourSpeciesRoutePolicy.propose(
                SwarmAgentArchetype.BREACHER,SELF,
                new Vec2(Double.NaN,0),TARGET,FORWARD,
                List.of(WAYPOINT),1,true,true);
        assertFalse(p.eligible());
        assertFalse(propose(SwarmAgentArchetype.FLANKER,List.of()).eligible());
        assertNull(SwarmFourSpeciesRoutePolicy.choose(
                null,true,true,0,0).destination());
    }
}
