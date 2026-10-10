package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmRangedSpacingPolicyTest {
    @Test void closeObservedPlayerTriggersShortBoundedGameReposition() {
        var plan = SwarmRangedSpacingPolicy.consider(
                new Vec2(3,0), new Vec2(0,0), true, 1.0);
        assertTrue(plan.active());
        assertEquals(new Vec2(6,0), plan.candidate());
        assertTrue(plan.candidate().subtract(new Vec2(3,0)).length()
                <= SwarmRangedSpacingPolicy.STEP_BLOCKS);
    }

    @Test void normalShootingDistanceNeedsNoReposition() {
        var plan = SwarmRangedSpacingPolicy.consider(
                new Vec2(9,0), new Vec2(0,0), true, 1.0);
        assertFalse(plan.active());
    }

    @Test void neverUseStaleOrLowConfidenceMinecraftPlayerPosition() {
        assertFalse(SwarmRangedSpacingPolicy.consider(
                new Vec2(3,0), new Vec2(0,0), false, 1.0).active());
        assertFalse(SwarmRangedSpacingPolicy.consider(
                new Vec2(3,0), new Vec2(0,0), true, 0.4).active());
    }

    @Test void overlappingOrNonFinitePositionsCannotInventEscapeRoute() {
        assertFalse(SwarmRangedSpacingPolicy.consider(
                new Vec2(0,0),new Vec2(0,0),true,1).active());
        assertFalse(SwarmRangedSpacingPolicy.consider(
                new Vec2(Double.NaN,0),new Vec2(0,0),true,1).active());
    }

    @Test void retreatNeverSurpassesDesiredGameComfortBand() {
        var p = SwarmRangedSpacingPolicy.consider(
                new Vec2(5.9,0),new Vec2(0,0),true,1);
        assertTrue(p.active());
        assertEquals(7.5,p.candidate().x(),1e-9);
    }
}
