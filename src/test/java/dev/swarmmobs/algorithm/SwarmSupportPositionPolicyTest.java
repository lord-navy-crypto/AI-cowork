package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.agent.SwarmAgentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Geometric cost and admissible-candidate tests, not combat turns. */
class SwarmSupportPositionPolicyTest {
    private static SwarmSupportPositionPolicy.SupportDecision choose(
            Vec2 position, Vec2 positive, Vec2 negative, List<Vec2> peers,
            int currentSide, boolean plusAllowed, boolean minusAllowed) {
        return SwarmSupportPositionPolicy.chooseSupport(
                position,positive,negative,peers,2.4,0.6,currentSide,0,
                plusAllowed,minusAllowed,true,true);
    }

    @Test void selectionRespondsToActualTravelDistance() {
        var selection=choose(new Vec2(2,0),new Vec2(3,0),
                new Vec2(-3,0),List.of(),-1,true,true);
        assertEquals(1,selection.side());
        assertEquals(1.0,selection.chosenCost(),1e-9);
        assertTrue(selection.clearanceVerified());
    }

    @Test void smallNoisyImprovementsCannotCauseOscillation() {
        var state=new SwarmAgentState();
        var self=new Vec2(0,0);
        state.acceptSupportPositionDecision(choose(self,new Vec2(2,0),
                new Vec2(-1.8,0),List.of(),0,true,false));
        for(int tick=0;tick<10_000;tick+=6) {
            double next= tick%12==0 ? -1.8 : -1.9;
            var choice=choose(self,new Vec2(2,0),new Vec2(next,0),
                    List.of(),state.supportPositionSide(),true,true);
            state.acceptSupportPositionDecision(choice);
        }
        assertEquals(1,state.supportPositionSide());
        assertEquals(0,state.supportPositionSwitches());
        assertTrue(state.supportPositionFeasibleSamples()>1000);
    }

    @Test void hardCorridorSafetyOverridesSwitchCost() {
        var selection=choose(new Vec2(100,0),new Vec2(2,0),
                new Vec2(-2,0),List.of(),+1,false,true);
        assertEquals(-1,selection.side());
        assertEquals(102,selection.chosenCost(),1e-9);
        assertTrue(selection.clearanceVerified());
    }

    @Test void realNeighborClearanceChangesChoice() {
        var choice=choose(new Vec2(0,0),new Vec2(2,0),
                new Vec2(-2,0),List.of(new Vec2(2,0)),0,true,true);
        assertEquals(-1,choice.side());
        assertEquals(2.0,choice.chosenCost(),1e-9);
        assertEquals(4.4,SwarmSupportPositionPolicy.localCost(
                new Vec2(0,0),new Vec2(2,0),
                List.of(new Vec2(2,0)),2.4),1e-9);
    }

    @Test void malformedAndBlockedCandidatesNeverWin() {
        var origin=new Vec2(0,0);
        var plus=new Vec2(2,0);
        var minus=new Vec2(-2,0);
        var blocked=choose(origin,plus,minus,List.of(),1,false,false);
        assertEquals(0,blocked.side());
        assertFalse(blocked.clearanceVerified());
        var invalid=choose(origin,new Vec2(Double.NaN,0),minus,
                List.of(),-1,true,true);
        assertEquals(-1,invalid.side()); // choose valid alternative
        assertEquals(2.0,invalid.chosenCost(),1e-9);
        var friendlyBlocked=SwarmSupportPositionPolicy.chooseSupport(
                origin,plus,minus,List.of(),2.4,0.6,0,0,
                true,true,false,true);
        assertEquals(-1,friendlyBlocked.side());
        assertEquals(0,choose(origin,plus,minus,List.of(),0,false,false).side());
    }

    @Test void gridComparisonNeverIncreasesLocalCostAgainstLegacyFixedSide() {
        var plus=new Vec2(3,0);
        var minus=new Vec2(-3,0);
        for(int ix=-10;ix<=10;ix++) for(int iz=-5;iz<=5;iz++) {
            var self=new Vec2(ix*.4,iz*.4);
            var peers=List.of(new Vec2(3,0),new Vec2(2.8,.4));
            var optimized=choose(self,plus,minus,peers,0,true,true);
            double fixedSideCost=SwarmSupportPositionPolicy.localCost(
                    self,plus,peers,2.4);
            assertTrue(optimized.chosenCost()<=fixedSideCost+1e-9);
        }
    }

    @Test void retainedLaneCountsRealSwitchesAndResetsForNewTarget() {
        var state=new SwarmAgentState();
        state.acceptSupportPositionDecision(choose(new Vec2(2,0),
                new Vec2(3,0),new Vec2(-3,0),List.of(),0,true,true));
        assertEquals(1,state.supportPositionSide());
        state.acceptSupportPositionDecision(choose(new Vec2(0,0),
                new Vec2(2,0),new Vec2(-1.9,0),List.of(),
                state.supportPositionSide(),true,true));
        assertEquals(1,state.supportPositionSide());
        assertEquals(0,state.supportPositionSwitches());
        state.acceptSupportPositionDecision(choose(new Vec2(-2,0),
                new Vec2(3,0),new Vec2(-3,0),List.of(),+1,true,true));
        assertEquals(-1,state.supportPositionSide());
        assertEquals(1,state.supportPositionSwitches());
        state.acceptSupportPositionDecision(choose(new Vec2(0,0),
                new Vec2(2,0),new Vec2(-2,0),List.of(),-1,false,false));
        assertEquals(0,state.supportPositionSide());
        assertEquals(1,state.supportPositionUnavailableSamples());
        state.bindTacticalTarget(UUID.fromString("ace215c7-d64b-4c12-b226-fc08e7d9f11c"));
        assertEquals(0,state.supportPositionSide());
        assertEquals(0,state.supportPositionSwitches());
        assertEquals(0,state.supportPositionFeasibleSamples());
    }
}
