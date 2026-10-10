package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SwarmMixedSquadSafetyPolicyTest {
    @Test void supportLaneOnlyBlocksAlliesActuallyBetweenSkeletonAndTarget() {
        var skeleton=new Vec2(0,0);
        var target=new Vec2(10,0);
        assertFalse(SwarmFriendlyFireLanePolicy.isClear(
                skeleton,target,List.of(new Vec2(5,0))));
        assertFalse(SwarmFriendlyFireLanePolicy.isClear(
                skeleton,target,List.of(new Vec2(6,1))));
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                skeleton,target,List.of(new Vec2(5,3))));
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                skeleton,target,List.of(new Vec2(-4,0))));
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                skeleton,target,List.of(new Vec2(15,0))));
        assertTrue(SwarmFriendlyFireLanePolicy.isClear(
                skeleton,target,List.of()));
        assertFalse(SwarmFriendlyFireLanePolicy.isClear(
                skeleton,skeleton,List.of()));
    }

    @Test void crossSpeciesSafeSideOverridesParityButPreservesStableFallback() {
        assertEquals(-1.0,SwarmFriendlyFireLanePolicy.chooseSide(
                0,true,true,false,true));
        assertEquals(1.0,SwarmFriendlyFireLanePolicy.chooseSide(
                1,true,true,true,false));
        assertEquals(1.0,SwarmFriendlyFireLanePolicy.chooseSide(
                0,false,false,false,false));
        assertEquals(-1.0,SwarmFriendlyFireLanePolicy.chooseSide(
                1,true,true,true,true));
        assertEquals(-1.0,SwarmFriendlyFireLanePolicy.chooseSide(
                0,false,true,true,true));
    }

    @Test void creeperDangerMustBeActiveAndOnTheSameSharedTarget() {
        var player=UUID.randomUUID();
        var other=UUID.randomUUID();
        assertFalse(SwarmBreacherSafetyPolicy.mustYield(
                player,player,false,false,1));
        assertFalse(SwarmBreacherSafetyPolicy.mustYield(
                player,other,true,false,1));
        assertFalse(SwarmBreacherSafetyPolicy.mustYield(
                null,player,true,false,1));
        assertFalse(SwarmBreacherSafetyPolicy.mustYield(
                player,player,true,false,30));
        assertTrue(SwarmBreacherSafetyPolicy.mustYield(
                player,player,true,false,9));
        assertTrue(SwarmBreacherSafetyPolicy.mustYield(
                player,player,false,true,25));
    }

    @Test void zombieRetreatFromCreeperIsFiniteAndDeterministicEvenIfOverlapping() {
        var zombie=new Vec2(2,2);
        var creeper=new Vec2(1,2);
        var retreat=SwarmBreacherSafetyPolicy.stepAway(zombie,creeper,3);
        assertTrue(retreat.x()>zombie.x());
        assertEquals(zombie.z(),retreat.z(),1e-9);
        var overlap=SwarmBreacherSafetyPolicy.stepAway(zombie,zombie,5);
        assertNotNull(overlap);
        assertEquals(SwarmBreacherSafetyPolicy.ESCAPE_STEP,
                overlap.subtract(zombie).length(),1e-9);
        assertEquals(overlap,SwarmBreacherSafetyPolicy.stepAway(zombie,zombie,5));
    }
}
