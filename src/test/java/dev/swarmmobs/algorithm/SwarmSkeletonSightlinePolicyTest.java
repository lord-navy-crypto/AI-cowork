package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.agent.SwarmRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmSkeletonSightlinePolicyTest {
    @Test void gameLanesArePerpendicularToVisiblePlayerNotCameraYaw() {
        var c=SwarmSkeletonSightlinePolicy.propose(
                new Vec2(0,0),new Vec2(10,0));
        assertTrue(c.valid());
        assertEquals(new Vec2(0,2.5),c.left());
        assertEquals(new Vec2(0,-2.5),c.right());
    }

    @Test void playerOcclusionOrTerrainChecksRejectUnsafeCandidate() {
        var c=SwarmSkeletonSightlinePolicy.propose(
                new Vec2(0,0),new Vec2(10,0));
        assertNull(SwarmSkeletonSightlinePolicy.choose(c,false,false,0));
        assertEquals(c.left(),
                SwarmSkeletonSightlinePolicy.choose(c,true,false,1));
        assertEquals(c.right(),
                SwarmSkeletonSightlinePolicy.choose(c,false,true,0));
    }

    @Test void bothClearMinecraftLanesUseDeterministicSlot() {
        var c=SwarmSkeletonSightlinePolicy.propose(
                new Vec2(0,0),new Vec2(0,10));
        assertEquals(c.left(),
                SwarmSkeletonSightlinePolicy.choose(c,true,true,0));
        assertEquals(c.right(),
                SwarmSkeletonSightlinePolicy.choose(c,true,true,1));
    }

    @Test void timedOutGameLaneSelectsOtherVerifiedSide() {
        var pair = SwarmSkeletonSightlinePolicy.propose(
                new Vec2(0, 0), new Vec2(10, 0));
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(30, 1));
        state.updateLocalPlan(0, 0, SwarmRole.REAR_PRESSURE,
                pair.left().x(), pair.left().z(), 0, 0);
        state.updateRangedSpacing(true, 100);
        assertTrue(state.expireRangedSpacing(130));

        var side = SwarmSkeletonSightlinePolicy.choose(
                pair,
                state.canUseRangedWaypoint(pair.left().x(), pair.left().z(), 180),
                state.canUseRangedWaypoint(pair.right().x(), pair.right().z(), 180),
                0);
        assertEquals(pair.right(), side,
                "Skeleton must avoid the same timed-out left lane");
        assertEquals(pair.left(), SwarmSkeletonSightlinePolicy.choose(
                pair, true, true, 0),
                "Without failed-waypoint evidence the stable slot remains deterministic");
    }

    @Test void invalidSightingAndOverlappingEntitiesAreNeverActionable() {
        assertFalse(SwarmSkeletonSightlinePolicy.propose(
                new Vec2(0,0),new Vec2(0,0)).valid());
        assertFalse(SwarmSkeletonSightlinePolicy.propose(
                new Vec2(Double.NaN,0),new Vec2(2,0)).valid());
        assertNull(SwarmSkeletonSightlinePolicy.choose(null,true,true,0));
    }

    @Test void lateralShiftIsBoundedToShortMinecraftStep() {
        var c=SwarmSkeletonSightlinePolicy.propose(
                new Vec2(3,3),new Vec2(3,15));
        assertEquals(2.5,c.left().subtract(new Vec2(3,3)).length(),1e-9);
        assertEquals(2.5,c.right().subtract(new Vec2(3,3)).length(),1e-9);
    }
}
