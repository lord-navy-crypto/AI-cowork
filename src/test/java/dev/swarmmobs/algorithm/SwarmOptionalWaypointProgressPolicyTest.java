package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static dev.swarmmobs.algorithm.SwarmOptionalWaypointProgressPolicy.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class SwarmOptionalWaypointProgressPolicyTest {
    @Test void doesNotStopLegitimateMovementDuringFirstSample() {
        assertEquals(WAIT, SwarmOptionalWaypointProgressPolicy.assess(
                109, 100, 8, 7.95, 0, false));
        assertEquals(PROGRESS, SwarmOptionalWaypointProgressPolicy.assess(
                112, 100, 8, 7.2, 0, false));
    }

    @Test void lackOfDistanceImprovementIsRealStallEvenIfMobMovedElsewhere() {
        assertEquals(STALLED, SwarmOptionalWaypointProgressPolicy.assess(
                112, 100, 8, 8.0, 0, false));
        assertEquals(STALLED, SwarmOptionalWaypointProgressPolicy.assess(
                112, 100, 8, 7.80, 0, false));
        assertEquals(PROGRESS, SwarmOptionalWaypointProgressPolicy.assess(
                112, 100, 8, 7.75, 0, false));
    }

    @Test void finishedNavigationWithoutArrivalStopsOptionalMoveSooner() {
        assertEquals(WAIT, SwarmOptionalWaypointProgressPolicy.assess(
                105, 100, 8, 8, 0, true));
        assertEquals(STALLED, SwarmOptionalWaypointProgressPolicy.assess(
                106, 100, 8, 8, 0, true));
        assertEquals(ARRIVED, SwarmOptionalWaypointProgressPolicy.assess(
                106, 100, 8, 1.0, 0, true));
    }

    @Test void newWaypointNeedsFreshSampleRatherThanFalseStall() {
        assertEquals(RESET, SwarmOptionalWaypointProgressPolicy.assess(
                150, 100, 8, 8, 1.5, false));
        assertEquals(WAIT, SwarmOptionalWaypointProgressPolicy.assess(
                106, 100, 8, 8, 1.49, false));
    }

    @Test void onlyIssuedMatchingMinecraftPathCanSupplyProgressEvidence() {
        assertTrue(SwarmOptionalWaypointProgressPolicy.matchesCommand(
                true, 10, 5, 10, 5));
        assertTrue(SwarmOptionalWaypointProgressPolicy.matchesCommand(
                true, 10.3, 5, 10, 5));
        assertFalse(SwarmOptionalWaypointProgressPolicy.matchesCommand(
                false, 10, 5, 10, 5));
        assertFalse(SwarmOptionalWaypointProgressPolicy.matchesCommand(
                true, 12, 5, 10, 5));
        assertFalse(SwarmOptionalWaypointProgressPolicy.matchesCommand(
                true, Double.NaN, 5, 10, 5));
        assertFalse(SwarmOptionalWaypointProgressPolicy.matchesCommand(
                true, 10, 5, Double.POSITIVE_INFINITY, 5));
    }

    @Test void invalidOrRegressingTicksCannotInventProgress() {
        assertEquals(RESET, SwarmOptionalWaypointProgressPolicy.assess(
                -1, 100, 8, 8, 0, false));
        assertEquals(RESET, SwarmOptionalWaypointProgressPolicy.assess(
                99, 100, 8, 8, 0, false));
        assertEquals(RESET, SwarmOptionalWaypointProgressPolicy.assess(
                110, 100, Double.NaN, 8, 0, false));
        assertEquals(RESET, SwarmOptionalWaypointProgressPolicy.assess(
                110, 100, 8, Double.POSITIVE_INFINITY, 0, false));
        assertEquals(RESET, SwarmOptionalWaypointProgressPolicy.assess(
                110, 100, 8, 8, -1, false));
    }

    @Test void arrivalAndImprovementAreDifferentFromMotionWithoutProgress() {
        assertEquals(ARRIVED, SwarmOptionalWaypointProgressPolicy.assess(
                112, 100, 3.0, 1.2, 0, false));
        assertEquals(STALLED, SwarmOptionalWaypointProgressPolicy.assess(
                112, 100, 3.0, 3.7, 0, false));
    }
}
