package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmColonyGatherPolicyTest {
    @Test
    void scarceFoodGetsPriorityAndFullSuppliesAreNotCollected() {
        var food = SwarmNestColonyPolicy.Kind.NUTRIENT;
        var soil = SwarmNestColonyPolicy.Kind.SOIL;
        var logs = SwarmNestColonyPolicy.Kind.TIMBER;
        assertTrue(SwarmColonyGatherPolicy.needs(food, 8, 6, 0, 0));
        assertTrue(SwarmColonyGatherPolicy.needs(soil, 0, 6, 12, 0));
        assertTrue(SwarmColonyGatherPolicy.needs(logs, 8, 0, 12, 0));
        assertFalse(SwarmColonyGatherPolicy.needs(food, 0, 0, 12, 0));
        assertFalse(SwarmColonyGatherPolicy.needs(soil, 8, 0, 0, 0));
        assertFalse(SwarmColonyGatherPolicy.needs(logs, 0, 6, 0, 0));
        assertFalse(SwarmColonyGatherPolicy.needs(food, 0, 0, 0, 128));
        assertFalse(SwarmColonyGatherPolicy.needs(food, 0, 0, 0, 126));
        assertTrue(SwarmColonyGatherPolicy.score(food, 16, 0, 0, 0)
                < SwarmColonyGatherPolicy.score(logs, 16, 0, 0, 0));
        assertTrue(SwarmColonyGatherPolicy.score(logs, 25, 0, 0, 12)
                > SwarmColonyGatherPolicy.score(logs, 4, 0, 0, 12));
        assertEquals(Double.POSITIVE_INFINITY,
                SwarmColonyGatherPolicy.score(null, 1, 0, 0, 0));
    }

    @Test
    void stagnantOrOverdueJobsEndInsteadOfHoldingTheMobForever() {
        assertFalse(SwarmColonyGatherPolicy.expired(20, 0, 10));
        assertTrue(SwarmColonyGatherPolicy.expired(200, 0, 190));
        assertTrue(SwarmColonyGatherPolicy.expired(90, 0, 0));
        assertTrue(SwarmColonyGatherPolicy.expired(9, 10, 10));
    }
}
