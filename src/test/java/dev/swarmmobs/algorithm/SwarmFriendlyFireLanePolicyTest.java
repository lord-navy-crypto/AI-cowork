package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SwarmFriendlyFireLanePolicyTest {

    @Test void sharedTargetZombieInsideShotCorridorBlocksBowHandoff() {
        Vec2 skeleton = new Vec2(0, 0);
        Vec2 target = new Vec2(12, 0);
        assertFalse(SwarmFriendlyFireLanePolicy.isClear(
                skeleton, target, List.of(new Vec2(6, 0))));
    }

    @Test void offsetZombieAllowsVanillaRangedAttackHandoff() {
        Vec2 skeleton = new Vec2(0, 0);
        Vec2 target = new Vec2(12, 0);
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                skeleton, target, List.of(new Vec2(6, 3))));
    }

    @Test void alliedCreeperBehindShooterDoesNotBlockTheShot() {
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                new Vec2(0, 0), new Vec2(12, 0),
                List.of(new Vec2(-2, 0))));
    }

    @Test void noKnownAlliesMeansNoInventedFriendlyFireRisk() {
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                new Vec2(0, 0), new Vec2(12, 0), List.of()));
    }

    @Test void invalidAndZeroLengthCorridorsAreNeverCalledSafe() {
        assertFalse(SwarmFriendlyFireLanePolicy.isClear(
                new Vec2(0, 0), new Vec2(0, 0), List.of()));
        assertFalse(SwarmFriendlyFireLanePolicy.isClear(
                new Vec2(Double.NaN, 0), new Vec2(12, 0), List.of()));
    }
}
