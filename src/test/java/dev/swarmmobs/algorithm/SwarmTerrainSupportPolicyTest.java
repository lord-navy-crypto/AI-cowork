package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmTerrainSupportPolicyTest {

    @Test
    void acceptsImmediateGroundSupport() {
        assertTrue(SwarmTerrainSupportPolicy.hasSupport(
                new boolean[]{true, false, false},
                1
        ));
    }

    @Test
    void acceptsSupportWithinConfiguredDrop() {
        assertTrue(SwarmTerrainSupportPolicy.hasSupport(
                new boolean[]{false, true, false},
                1
        ));
    }

    @Test
    void rejectsSupportDeeperThanConfiguredDrop() {
        assertFalse(SwarmTerrainSupportPolicy.hasSupport(
                new boolean[]{false, false, true},
                1
        ));
    }

    @Test
    void rejectsCompletelyUnsupportedProbe() {
        assertFalse(SwarmTerrainSupportPolicy.hasSupport(
                new boolean[]{false, false, false},
                2
        ));
    }

    @Test
    void clampsNegativeDropDepthToZero() {
        assertTrue(SwarmTerrainSupportPolicy.hasSupport(
                new boolean[]{true, false},
                -3
        ));
        assertFalse(SwarmTerrainSupportPolicy.hasSupport(
                new boolean[]{false, true},
                -3
        ));
    }
}
