package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmOptionalGameWaypointCommitmentPolicyTest {
    private static final Vec2 SELF = new Vec2(0,0);
    private static final Vec2 OLD = new Vec2(3,0);

    @Test void movingSkeletonKeepsShortExistingGoalDespiteReplannedOffset() {
        assertTrue(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,0.95,SELF,OLD,new Vec2(4,0)));
        assertTrue(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,0.95,new Vec2(1.25,0),OLD,new Vec2(4.25,0)));
    }

    @Test void reachedOrFarAwayGameWaypointIsNotHeld() {
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,1,new Vec2(2.4,0),OLD,new Vec2(4,0)));
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,1,new Vec2(-2,0),OLD,new Vec2(4,0)));
    }

    @Test void targetRepositionOrLaneSwitchEndsOldCommitment() {
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,1,SELF,OLD,new Vec2(-3,0)));
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,false,1,SELF,OLD,new Vec2(3.5,0)));
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,0.5,SELF,OLD,new Vec2(3.5,0)));
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                false,true,1,SELF,OLD,new Vec2(3.5,0)));
    }

    @Test void invalidPositionOrConfidenceNeverAuthorizesMove() {
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,Double.NaN,SELF,OLD,new Vec2(4,0)));
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,1,SELF,new Vec2(Double.NaN,0),new Vec2(4,0)));
        assertFalse(SwarmOptionalGameWaypointCommitmentPolicy.keep(
                true,true,1,SELF,OLD,new Vec2(Double.POSITIVE_INFINITY,0)));
    }

    @Test void localWaypointsUseMobHeightNotPlayerHeight() {
        assertEquals(64,SwarmOptionalGameWaypointCommitmentPolicy.chooseNavigationHeight(
                true,64,83));
        assertEquals(83,SwarmOptionalGameWaypointCommitmentPolicy.chooseNavigationHeight(
                false,64,83));
        assertEquals(83,SwarmOptionalGameWaypointCommitmentPolicy.chooseNavigationHeight(
                true,Double.NaN,83));
    }
}
