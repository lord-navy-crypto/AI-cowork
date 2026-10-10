package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmAgentState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmEngagementPolicyTest {
    @Test void independentWorkerReceivesAlertThenTrueFightThenRecoveryThenReturnsToWork() {
        SwarmAgentState worker = new SwarmAgentState();
        assertEquals(SwarmEngagementPolicy.Mode.WORK,worker.engagementMode());
        worker.updateEngagement(true,false,10); // message without line of sight
        assertEquals(SwarmEngagementPolicy.Mode.ALERT,worker.engagementMode());
        assertFalse(SwarmEngagementPolicy.canDoNestWork(worker.engagementMode()));
        assertFalse(SwarmEngagementPolicy.enableBattleRounds(worker.engagementMode(),5));

        worker.updateEngagement(true,true,12); // actual sighting / fresh ally
        assertEquals(SwarmEngagementPolicy.Mode.COMBAT,worker.engagementMode());
        assertTrue(SwarmEngagementPolicy.enableBattleRounds(worker.engagementMode(),1));
        assertFalse(SwarmEngagementPolicy.enableBattleRounds(worker.engagementMode(),0));
        worker.updateEngagement(true,false,25); // brief drop in direct vision
        assertEquals(SwarmEngagementPolicy.Mode.COMBAT,worker.engagementMode());
        worker.updateEngagement(true,false,37); // beyond 20 tick grace
        assertEquals(SwarmEngagementPolicy.Mode.ALERT,worker.engagementMode());
        worker.updateEngagement(false,false,40);
        assertEquals(SwarmEngagementPolicy.Mode.RECOVERY,worker.engagementMode());
        worker.updateEngagement(false,false,69);
        assertEquals(SwarmEngagementPolicy.Mode.RECOVERY,worker.engagementMode());
        worker.updateEngagement(false,false,70);
        assertEquals(SwarmEngagementPolicy.Mode.WORK,worker.engagementMode());
        assertTrue(SwarmEngagementPolicy.canDoNestWork(worker.engagementMode()));
    }

    @Test void noRelayMeansNoFakeCombatAndDisengagementIsNotPermanent() {
        assertEquals(SwarmEngagementPolicy.Mode.WORK, SwarmEngagementPolicy.next(
                SwarmEngagementPolicy.Mode.WORK,false,false,Long.MAX_VALUE,Long.MAX_VALUE));
        assertEquals(SwarmEngagementPolicy.Mode.ALERT, SwarmEngagementPolicy.next(
                SwarmEngagementPolicy.Mode.WORK,true,false,Long.MAX_VALUE,Long.MAX_VALUE));
        assertEquals(SwarmEngagementPolicy.Mode.RECOVERY, SwarmEngagementPolicy.next(
                SwarmEngagementPolicy.Mode.COMBAT,false,false,0,0));
        assertEquals(SwarmEngagementPolicy.Mode.WORK, SwarmEngagementPolicy.next(
                SwarmEngagementPolicy.Mode.RECOVERY,false,false,999,30));
    }

    @Test void allCasteCombatPhasesAreSmallAndLeaveVanillaGoalAuthorityAlone() {
        for(var archetype:SwarmAgentArchetype.values()) {
            assertEquals(1.0,SwarmTacticalRoundPolicy.formationRadiusMultiplier(
                    SwarmTacticalRoundPolicy.Phase.HOLD,archetype),1e-10);
            for(var phase:SwarmTacticalRoundPolicy.Phase.values()) {
                double value=SwarmTacticalRoundPolicy.formationRadiusMultiplier(
                        phase,archetype);
                assertTrue(Double.isFinite(value));
                assertTrue(value>=.95 && value<=1.12);
            }
        }
        assertFalse(SwarmEngagementPolicy.enableBattleRounds(
                SwarmEngagementPolicy.Mode.WORK,4));
        assertFalse(SwarmEngagementPolicy.enableBattleRounds(
                SwarmEngagementPolicy.Mode.RECOVERY,4));
        assertFalse(SwarmEngagementPolicy.enableBattleRounds(
                SwarmEngagementPolicy.Mode.ALERT,4));
    }

    @Test void renewedThreatCancelsRecoveryWithoutRememberingAnOldTarget() {
        var state=new SwarmAgentState();
        state.updateEngagement(true,true,100);
        state.updateEngagement(false,false,107);
        state.updateEngagement(true,false,113);
        assertEquals(SwarmEngagementPolicy.Mode.ALERT,state.engagementMode());
        state.updateEngagement(true,true,114);
        assertEquals(SwarmEngagementPolicy.Mode.COMBAT,state.engagementMode());
        state.updateEngagement(false,false,120);
        state.updateEngagement(false,false,150);
        assertEquals(SwarmEngagementPolicy.Mode.WORK,state.engagementMode());
    }
}
