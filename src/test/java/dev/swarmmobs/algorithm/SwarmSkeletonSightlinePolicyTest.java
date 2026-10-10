package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
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
