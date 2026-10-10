package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmZombieBowLaneYieldPolicyTest {
    private static final Vec2 SHOOTER = new Vec2(0, 0);
    private static final Vec2 PLAYER = new Vec2(10, 0);
    private static final Vec2 ZOMBIE = new Vec2(5, 0);

    @Test void zombieCanYieldAFriendlySkeletonLaneWithoutChangingVanillaAttacks() {
        var plan = SwarmZombieBowLaneYieldPolicy.propose(
                ZOMBIE, ZOMBIE, SHOOTER, PLAYER, true, 1.0);
        assertTrue(plan.applicable());
        assertEquals(new Vec2(5, 2.5), plan.left());
        assertEquals(new Vec2(5, -2.5), plan.right());
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                SHOOTER, PLAYER, List.of(plan.left())));
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                SHOOTER, PLAYER, List.of(plan.right())));
    }

    @Test void alreadySafeZombieWaypointIsNotNeedlesslyDiverted() {
        var plan = SwarmZombieBowLaneYieldPolicy.propose(
                ZOMBIE, new Vec2(5, 3), SHOOTER, PLAYER, true, 1.0);
        assertFalse(plan.applicable());
    }

    @Test void neverInterruptsZombieAlreadyNearVanillaMeleeEnvelope() {
        var plan = SwarmZombieBowLaneYieldPolicy.propose(
                new Vec2(7.5, 0), new Vec2(5, 0),
                SHOOTER, PLAYER, true, 1.0);
        assertFalse(plan.applicable());
        var atBoundary = SwarmZombieBowLaneYieldPolicy.propose(
                new Vec2(5.5, 0), new Vec2(5, 0),
                SHOOTER, PLAYER, true, 1.0);
        assertFalse(atBoundary.applicable());
    }

    @Test void onlyRealDirectGameObservationsCanInfluenceMovement() {
        assertFalse(SwarmZombieBowLaneYieldPolicy.propose(
                ZOMBIE, ZOMBIE, SHOOTER, PLAYER, false, 1.0).applicable());
        assertFalse(SwarmZombieBowLaneYieldPolicy.propose(
                ZOMBIE, ZOMBIE, SHOOTER, PLAYER, true, 0.4).applicable());
        assertFalse(SwarmZombieBowLaneYieldPolicy.propose(
                ZOMBIE, ZOMBIE, SHOOTER,
                new Vec2(Double.NaN, 0), true, 1.0).applicable());
    }

    @Test void neverUsesFarDistantSkeletonOrDegenerateFireLine() {
        assertFalse(SwarmZombieBowLaneYieldPolicy.propose(
                ZOMBIE, ZOMBIE, new Vec2(-30, 0), PLAYER,
                true, 1.0).applicable());
        assertFalse(SwarmZombieBowLaneYieldPolicy.propose(
                new Vec2(5, 0), new Vec2(5, 0),
                new Vec2(10, 0), PLAYER, true, 1.0).applicable());
    }

    @Test void chooseOnlyValidLocallyWalkableGameSquare() {
        var plan = SwarmZombieBowLaneYieldPolicy.propose(
                ZOMBIE, ZOMBIE, SHOOTER, PLAYER, true, 1.0);
        assertNull(SwarmZombieBowLaneYieldPolicy.choose(plan, false, false, 0));
        assertEquals(plan.right(),
                SwarmZombieBowLaneYieldPolicy.choose(plan, false, true, 0));
        assertEquals(plan.left(),
                SwarmZombieBowLaneYieldPolicy.choose(plan, true, false, 1));
        assertEquals(plan.left(),
                SwarmZombieBowLaneYieldPolicy.choose(plan, true, true, 2));
        assertEquals(plan.right(),
                SwarmZombieBowLaneYieldPolicy.choose(plan, true, true, 3));
        assertNull(SwarmZombieBowLaneYieldPolicy.choose(null, true, true, 0));
    }

    @Test void allObservedSkeletonLinesMustBeClearForSuggestedSquare() {
        var plan = SwarmZombieBowLaneYieldPolicy.propose(
                ZOMBIE, ZOMBIE, SHOOTER, PLAYER, true, 1.0);
        Vec2 secondShooter = new Vec2(5, 5);
        // The left proposal is clear for the primary Skeleton, but may
        // not be clear for a Skeleton looking down another game corridor.
        assertFalse(SwarmFriendlyFireLanePolicy.isClear(
                secondShooter, PLAYER, List.of(plan.left())));
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                SHOOTER, PLAYER, List.of(plan.left())));
    }
}
