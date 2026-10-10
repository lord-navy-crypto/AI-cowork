package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmFailedWaypointMemoryTest {
    @Test void keepsThreeSeparateFailedSquaresAndAvoidsABABOscillation() {
        var memory = new SwarmFailedWaypointMemory();
        memory.record(0,0,100,180,2);
        memory.record(5,0,110,180,2);
        memory.record(10,0,120,180,2);
        assertEquals(3,memory.activeCount(150));
        for (double x : new double[] {0,5,10}) {
            assertFalse(memory.allows(x,0,150,2));
        }
        assertTrue(memory.allows(15,0,150,2));
        assertFalse(memory.allows(1.9,0,150,2));
        assertTrue(memory.allows(2,0,150,2));
    }

    @Test void nearDuplicateUpdatesExistingSlotNotEvictOtherEvidence() {
        var memory = new SwarmFailedWaypointMemory();
        memory.record(0,0,100,180,2);
        memory.record(5,0,100,180,2);
        memory.record(.5,0,120,180,2);
        assertEquals(2,memory.activeCount(130));
        assertFalse(memory.allows(5,0,130,1));
        assertFalse(memory.allows(.5,0,130,1));
        assertFalse(memory.allows(0,0,280,1)); // refreshed nearby failure
        assertFalse(memory.allows(.5,0,280,1));
        assertTrue(memory.allows(.5,0,300,1)); // refreshed until 300
    }

    @Test void independentlyExpiresAndReusesOldCapacity() {
        var memory = new SwarmFailedWaypointMemory();
        memory.record(0,0,100,40,1);
        memory.record(5,0,110,100,1);
        memory.record(10,0,120,100,1);
        assertTrue(memory.allows(0,0,140,1));
        assertFalse(memory.allows(5,0,140,1));
        memory.record(15,0,150,100,1);
        assertEquals(3,memory.activeCount(160));
        assertFalse(memory.allows(5,0,160,1));
        assertFalse(memory.allows(10,0,160,1));
        assertFalse(memory.allows(15,0,160,1));
        memory.clear();
        assertEquals(0,memory.activeCount(160));
        assertTrue(memory.allows(15,0,160,1));
    }

    @Test void fullHistoryEvictsOneSlotInConstantSpace() {
        var memory = new SwarmFailedWaypointMemory();
        for (int i=0;i<4;i++) memory.record(i*5,0,100,100,1);
        assertEquals(3,memory.activeCount(101));
        int excluded = 0;
        for (int i=0;i<4;i++) if(!memory.allows(i*5,0,101,1)) excluded++;
        assertEquals(3,excluded);
    }

    @Test void neverStoresInvalidCoordinatesOrTicks() {
        var memory = new SwarmFailedWaypointMemory();
        memory.record(Double.NaN,0,100,180,2);
        memory.record(0,Double.POSITIVE_INFINITY,100,180,2);
        memory.record(0,0,-1,180,2);
        memory.record(0,0,100,0,2);
        memory.record(0,0,100,180,-1);
        assertEquals(0,memory.activeCount(150));
        assertFalse(memory.allows(Double.NaN,0,150,2));
        assertFalse(memory.allows(0,0,-1,2));
        assertTrue(memory.allows(0,0,150,2));
    }
}
