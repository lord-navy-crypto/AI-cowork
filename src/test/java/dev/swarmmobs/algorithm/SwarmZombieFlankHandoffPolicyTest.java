package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmZombieFlankHandoffPolicyTest {
    @Test void letsFlankerCompleteLastStepNearTargetBeforeMelee() {
        assertTrue(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.FLANK_LEFT,true,true,
                3.0*3.0,2.0*2.0,3.25));
        assertTrue(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.FLANK_RIGHT,true,true,
                3.0*3.0,2.0*2.0,3.25));
    }

    @Test void closeRangeAlwaysReturnsControlForVanillaMeleeAttack() {
        assertFalse(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.FLANK_LEFT,true,true,2.0*2.0,16,3.25));
    }

    @Test void frontLineZombieDoesNotDelayItsAttack() {
        assertFalse(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.CHASER,true,true,9,16,3.25));
    }

    @Test void neverForceMovementIfWaypointAlreadyReachedOrNotVisible() {
        assertFalse(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.FLANK_LEFT,true,true,9,0.5,3.25));
        assertFalse(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.FLANK_LEFT,false,true,9,16,3.25));
        assertFalse(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.FLANK_RIGHT,true,false,9,16,3.25));
    }

    @Test void respectsConfiguredEarlyMeleeReleaseAndInvalidInput() {
        assertFalse(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.FLANK_RIGHT,true,true,9,16,2.75));
        assertFalse(SwarmZombieFlankHandoffPolicy.finishWaypointBeforeMelee(
                SwarmRole.FLANK_RIGHT,true,true,Double.NaN,16,10));
    }
}
