package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestColonyPolicyTest {
    @Test
    void cannotDuplicateMatterOrExceedResourceCapacity() {
        assertEquals(0, SwarmNestColonyPolicy.acceptAmount(0, 40,
                SwarmNestColonyPolicy.Kind.NONE));
        assertEquals(1, SwarmNestColonyPolicy.acceptAmount(125, 5,
                SwarmNestColonyPolicy.Kind.TIMBER));
        assertEquals(0, SwarmNestColonyPolicy.acceptAmount(126, 4,
                SwarmNestColonyPolicy.Kind.TIMBER));
        assertEquals(128, SwarmNestColonyPolicy.MAX_STORED_RESOURCES);
    }

    @Test
    void growthRequiresEveryWorldSafetyPrecondition() {
        assertTrue(allowed(true, true, true, true, true, false,
                true, 12, 2, 12, 1200, 1200));
        assertFalse(allowed(false, true, true, true, true, false,
                true, 12, 2, 12, 1200, 1200));
        assertFalse(allowed(true, false, true, true, true, false,
                true, 12, 2, 12, 1200, 1200));
        assertFalse(allowed(true, true, false, true, true, false,
                true, 12, 2, 12, 1200, 1200));
        assertFalse(allowed(true, true, true, false, true, false,
                true, 12, 2, 12, 1200, 1200));
        assertFalse(allowed(true, true, true, true, false, false,
                true, 12, 2, 12, 1200, 1200));
        assertFalse(allowed(true, true, true, true, true, true,
                true, 12, 2, 12, 1200, 1200));
        assertFalse(allowed(true, true, true, true, true, false,
                false, 12, 2, 12, 1200, 1200));
        assertFalse(allowed(true, true, true, true, true, false,
                true, 11, 2, 12, 1200, 1200));
        assertFalse(allowed(true, true, true, true, true, false,
                true, 12, 12, 12, 1200, 1200));
        assertFalse(allowed(true, true, true, true, true, false,
                true, 12, 2, 12, 1199, 1200));
    }

    private static boolean allowed(boolean enabled, boolean natural, boolean difficulty,
                                  boolean chunk, boolean playerInRange, boolean tooClose,
                                  boolean site, int food, int count, int maximum,
                                  long now, long ready) {
        return SwarmNestColonyPolicy.canSpawn(enabled, natural, difficulty,
                chunk, playerInRange, tooClose, site, food, count,
                maximum, now, ready);
    }
}
