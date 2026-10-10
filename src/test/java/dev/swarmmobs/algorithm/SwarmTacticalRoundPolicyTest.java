package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmTacticalRoundPolicyTest {
    @Test
    void allAgentsInSameSquadSeeTheSameSynchronizedRound() {
        UUID target=UUID.fromString("e83b0d3e-f4b5-4f34-bcb7-f76397d6ee00");
        var first=SwarmTacticalRoundPolicy.phase(target,50);
        assertEquals(first,SwarmTacticalRoundPolicy.phase(target,99));
        var second=SwarmTacticalRoundPolicy.phase(target,150);
        var third=SwarmTacticalRoundPolicy.phase(target,250);
        assertNotEquals(first,second);
        assertNotEquals(second,third);
        assertNotEquals(first,third);
        assertEquals(first,SwarmTacticalRoundPolicy.phase(target,350));
        assertEquals(SwarmTacticalRoundPolicy.Phase.HOLD,
                SwarmTacticalRoundPolicy.phase(null,30));
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
    void vanillaCombatSafetyBeatsExtraRoundPreference() {
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
}
