package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmTacticalRoundPolicyTest {
    private static SwarmTacticalRoundPolicy.Signals cues(boolean fresh,
                                                         boolean support,
                                                         boolean frontline,
                                                         boolean crowded,
                                                         boolean blocked) {
        return new SwarmTacticalRoundPolicy.Signals(fresh,support,frontline,crowded,blocked);
    }

    @Test
    void noClockOnlyRotationForStableWorldEvenAcrossThousandsOfTicks() {
        var state=SwarmTacticalRoundPolicy.initial();
        var unchanged=cues(true,true,true,false,false);
        // What mattered before: 100-tick global phase boundaries.
        // What matters now: no new stimulus, no unnecessary movement.
        for(int t=0;t<=10_000;t+=6) {
            state=SwarmTacticalRoundPolicy.advance(state,
                    SwarmTacticalRoundPolicy.recommend(unchanged),t);
        }
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,state.phase());
        assertEquals(1,state.switchCount());
        assertNull(state.candidate());
    }

    @Test
    void crowdedSquadSwitchesOnlyAfterSignalConfirmationAndHoldTime() {
        var state=SwarmTacticalRoundPolicy.initial();
        var regular=cues(true,true,true,false,false);
        var crowded=cues(true,true,true,true,false);
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(regular),0);
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(regular),12);
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,state.phase());
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(crowded),18);
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,state.phase());
        // A transient noisy sample is not a new phase.
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(regular),24);
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,state.phase());
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(crowded),30);
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(crowded),42);
        assertEquals(SwarmTacticalRoundPolicy.Phase.ROTATE,state.phase());
        assertEquals(2,state.switchCount());
        // Even though the congestion clears, the rotation is given time.
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(regular),48);
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(regular),60);
        assertEquals(SwarmTacticalRoundPolicy.Phase.ROTATE,state.phase());
        state=SwarmTacticalRoundPolicy.advance(state,
                SwarmTacticalRoundPolicy.recommend(regular),66);
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,state.phase());
    }

    @Test
    void navigationFailureTriggersRotationWithoutWaitingForClockCycle() {
        assertEquals(SwarmTacticalRoundPolicy.Phase.ROTATE,
                SwarmTacticalRoundPolicy.recommend(cues(true,false,true,false,true)));
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,
                SwarmTacticalRoundPolicy.recommend(cues(false,true,true,true,true)));
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,
                SwarmTacticalRoundPolicy.recommend(cues(true,true,false,false,false)));
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,
                SwarmTacticalRoundPolicy.recommend(cues(true,true,true,false,false)));
    }

    @Test
    void rotationAlternatesOnlyWhenBothSupportLanesAreValid() {
        assertEquals(+1,SwarmTacticalRoundPolicy.supportSide(
                SwarmTacticalRoundPolicy.Phase.COVER,0,true,true,true,true));
        assertEquals(-1,SwarmTacticalRoundPolicy.supportSide(
                SwarmTacticalRoundPolicy.Phase.ROTATE,0,true,true,true,true));
        assertEquals(-1,SwarmTacticalRoundPolicy.supportSide(
                SwarmTacticalRoundPolicy.Phase.COVER,1,true,true,true,true));
        assertEquals(+1,SwarmTacticalRoundPolicy.supportSide(
                SwarmTacticalRoundPolicy.Phase.ROTATE,1,true,true,true,true));
    }

    @Test
    void supportCorridorSafetyOverridesAllPhasePreferences() {
        for(var phase:SwarmTacticalRoundPolicy.Phase.values()) {
            assertEquals(0,SwarmTacticalRoundPolicy.supportSide(
                    phase,0,false,false,true,true));
            assertEquals(0,SwarmTacticalRoundPolicy.supportSide(
                    phase,1,true,true,false,false));
            assertEquals(+1,SwarmTacticalRoundPolicy.supportSide(
                    phase,1,true,true,true,false));
            assertEquals(-1,SwarmTacticalRoundPolicy.supportSide(
                    phase,0,true,true,false,true));
        }
    }

    @Test
    void targetChangeOrDisablingResetsPendingPhaseWithoutContamination() {
        var pending=SwarmTacticalRoundPolicy.advance(
                SwarmTacticalRoundPolicy.initial(),SwarmTacticalRoundPolicy.Phase.COVER,50);
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,pending.phase());
        assertEquals(SwarmTacticalRoundPolicy.Phase.COVER,pending.candidate());
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,
                SwarmTacticalRoundPolicy.initial().phase());
        assertEquals(0,SwarmTacticalRoundPolicy.initial().switchCount());
    }
}
