package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestScoutSignalTest {
    @Test
    void liveObservationBelongsOnlyToOneNestAndExpires() {
        long nestA = 12345L;
        long nestB = 56789L;
        long spotted = 1000L;
        assertTrue(SwarmNestScoutSignal.freshFor(nestA, nestA, spotted, spotted));
        assertTrue(SwarmNestScoutSignal.freshFor(nestA, nestA, spotted,
                spotted + SwarmNestScoutSignal.FRESH_TICKS));
        assertFalse(SwarmNestScoutSignal.freshFor(nestA, nestA, spotted,
                spotted + SwarmNestScoutSignal.FRESH_TICKS + 1L));
        assertFalse(SwarmNestScoutSignal.freshFor(nestA, nestB, spotted,
                spotted + 1L));
        assertFalse(SwarmNestScoutSignal.freshFor(nestA, nestA, spotted, spotted - 1L));
    }
}
