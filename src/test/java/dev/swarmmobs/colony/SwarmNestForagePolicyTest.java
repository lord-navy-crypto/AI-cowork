package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmNestForagePolicyTest {
    @Test
    void onlyRipeRenewableBerriesHaveARealYield() {
        assertEquals(0, SwarmNestForagePolicy.yieldForAge(0));
        assertEquals(0, SwarmNestForagePolicy.yieldForAge(1));
        assertEquals(1, SwarmNestForagePolicy.yieldForAge(2));
        assertEquals(2, SwarmNestForagePolicy.yieldForAge(3));
        assertEquals(0, SwarmNestForagePolicy.yieldForAge(4));
    }

    @Test
    void requiresExplicitOperatorOptInLoadedNestAndAbsentPlayers() {
        assertTrue(SwarmNestForagePolicy.mayHarvest(
                true, true, true, true, false, 3, 0, 0));
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                false, true, true, true, false, 3, 0, 0));
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                true, false, true, true, false, 3, 0, 0));
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                true, true, false, true, false, 3, 0, 0));
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                true, true, true, false, false, 3, 0, 0));
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                true, true, true, true, true, 3, 0, 0));
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                true, true, true, true, false, 1, 0, 0));
    }

    @Test
    void foodDemandAndWholeItemStorageBlockWastefulPicking() {
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                true, true, true, true, false, 3, 0, 12));
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                true, true, true, true, false, 3, 128, 0));
        assertFalse(SwarmNestForagePolicy.mayHarvest(
                true, true, true, true, false, 3, 126, 0));
        assertTrue(SwarmNestForagePolicy.mayHarvest(
                true, true, true, true, false, 3, 124, 0));
    }

    @Test
    void idleWorkerGivesUpAnUnreachableBerryBush() {
        assertFalse(SwarmNestForagePolicy.expired(30, 20, 20));
        assertFalse(SwarmNestForagePolicy.expired(99, 20, 20));
        assertTrue(SwarmNestForagePolicy.expired(100, 20, 20));
        assertTrue(SwarmNestForagePolicy.expired(140, 0, 100));
        assertTrue(SwarmNestForagePolicy.expired(10, 20, 20));
    }
}
