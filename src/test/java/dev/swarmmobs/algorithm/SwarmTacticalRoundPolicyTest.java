package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.agent.SwarmAgentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmTacticalRoundPolicyTest {
    private static SwarmTacticalRoundPolicy.SupportDecision choose(
            Vec2 position, Vec2 positive, Vec2 negative, List<Vec2> peers,
            int currentSide, boolean plusAllowed, boolean minusAllowed) {
        return SwarmTacticalRoundPolicy.chooseSupport(
                position,positive,negative,peers,2.4,0.6,currentSide,0,
                plusAllowed,minusAllowed,true,true);
    }

    @Test
    void positionOptimizationRespondsToMeasuredTravelDistance() {
        var decision=choose(new Vec2(2,0),new Vec2(3,0),
                new Vec2(-3,0),List.of(),-1,true,true);
        assertEquals(1,decision.side());
        assertEquals(SwarmTacticalRoundPolicy.Phase.ROTATE,decision.phase());
        assertEquals(1.0,decision.chosenCost(),1e-9);
        assertTrue(decision.clearanceVerified());
    }

    @Test
    void stableLaneDoesNotOscillateForSmallImprovements() {
        var state=new SwarmAgentState();
        var self=new Vec2(0,0);
        // The initial safe side is already selected.
        state.acceptTacticalSupportDecision(choose(self,new Vec2(2,0),
                new Vec2(-1.8,0),List.of(),0,true,false));
        for(int tick=0;tick<10000;tick+=6) {
            // Alternate tiny estimates: opposite side is marginally
            // shorter but not by a full physical body width.
            double next= tick%12==0 ? -1.8 : -1.9;
            var plan=choose(self,new Vec2(2,0),new Vec2(next,0),
                    List.of(),state.tacticalSupportSide(),true,true);
            state.acceptTacticalSupportDecision(plan);
        }
        assertEquals(1,state.tacticalSupportSide());
        assertEquals(0,state.tacticalRoundSwitchCount());
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,
                state.tacticalRoundPhase());
    }

    @Test
    void unsafeCurrentSideOverridesAnySwitchingCost() {
        var decision=choose(new Vec2(100,0),new Vec2(2,0),
                new Vec2(-2,0),List.of(),+1,false,true);
        assertEquals(-1,decision.side());
        assertEquals(SwarmTacticalRoundPolicy.Phase.ROTATE,decision.phase());
        assertEquals(102,decision.chosenCost(),1e-9);
    }

    @Test
    void measuredNeighborCongestionCanOutweighTravelDistance() {
        // Both candidates are two blocks away. One violates the 2.4
        // block social-separation distance, the other does not.
        var choice=choose(new Vec2(0,0),new Vec2(2,0),
                new Vec2(-2,0),List.of(new Vec2(2,0)),0,true,true);
        assertEquals(-1,choice.side());
        assertEquals(2.0,choice.chosenCost(),1e-9);
        assertEquals(4.4,SwarmTacticalRoundPolicy.localCost(
                new Vec2(0,0),new Vec2(2,0),
                List.of(new Vec2(2,0)),2.4),1e-9);
    }

    @Test
    void invalidOrUnverifiedCorridorsCannotBeChosen() {
        var x=new Vec2(0,0);
        var pos=new Vec2(2,0);
        var neg=new Vec2(-2,0);
        var blocked=choose(x,pos,neg,List.of(),1,false,false);
        assertEquals(0,blocked.side());
        assertFalse(blocked.clearanceVerified());
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,blocked.phase());
        var invalid=choose(x,new Vec2(Double.NaN,0),neg,List.of(),-1,true,true);
        assertEquals(-1,invalid.side()); // use valid alternative safely
        assertEquals(2.0,invalid.chosenCost(),1e-9);
        var nearFriendly=SwarmTacticalRoundPolicy.chooseSupport(
                x,pos,neg,List.of(),2.4,0.6,0,0,
                true,true,false,true);
        assertEquals(-1,nearFriendly.side());
    }

    @Test
    void noTimersNorSpeciesDamageMultipliersAreRequired() {
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,
                SwarmTacticalRoundPolicy.classification(true,true));
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,
                SwarmTacticalRoundPolicy.classification(true,false));
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,
                SwarmTacticalRoundPolicy.classification(false,true));
    }

    @Test
    void realSupportSideChangesCountAndTargetChangesReset() {
        var state=new SwarmAgentState();
        state.acceptTacticalSupportDecision(choose(new Vec2(2,0),
                new Vec2(3,0),new Vec2(-3,0),List.of(),0,true,true));
        assertEquals(+1,state.tacticalSupportSide());
        state.acceptTacticalSupportDecision(choose(new Vec2(-2,0),
                new Vec2(3,0),new Vec2(-3,0),List.of(),+1,true,true));
        assertEquals(-1,state.tacticalSupportSide());
        assertEquals(1,state.tacticalRoundSwitchCount());
        state.bindTacticalTarget(UUID.fromString("ace215c7-d64b-4c12-b226-fc08e7d9f11c"));
        assertEquals(0,state.tacticalSupportSide());
        assertEquals(0,state.tacticalRoundSwitchCount());
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,state.tacticalRoundPhase());
    }
}
