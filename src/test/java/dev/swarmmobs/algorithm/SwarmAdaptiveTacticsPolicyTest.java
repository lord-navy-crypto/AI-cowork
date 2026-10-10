package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.algorithm.SwarmAdaptiveTacticsPolicy.Pattern;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SwarmAdaptiveTacticsPolicyTest {
    private static final UUID TARGET = new UUID(9L, 2L);
    private static final List<Vec2> GROUP = List.of(
            new Vec2(-4.0, -7.0), new Vec2(4.0, -7.0));

    private static TargetObservation sighting(long tick, double vx, double vz) {
        return new TargetObservation(TARGET,tick,0,64,0,0,1,vx,vz);
    }

    @Test void mobilePlayerTriggersDirectionAwareSweep() {
        var frame = SwarmAdaptiveTacticsPolicy.choose(
                sighting(100,0.2,0),100,1.0,GROUP,true);
        assertEquals(Pattern.SWEEP,frame.pattern());
        assertEquals(1.0,frame.forward().x(),1e-9);
        assertEquals(0.0,frame.forward().z(),1e-9);
        assertEquals(1.6,frame.leadBlocks(),1e-9);
    }

    @Test void stationaryTargetUsesActualLocalSquadApproachAxis() {
        var frame = SwarmAdaptiveTacticsPolicy.choose(
                sighting(100,0,0),100,1.0,GROUP,true);
        assertEquals(Pattern.SURROUND,frame.pattern());
        assertEquals(0.0,frame.forward().x(),1e-9);
        assertEquals(1.0,frame.forward().z(),1e-9);
    }

    @Test void solosPairsOrDisabledModesCannotPretendToHaveAnEncirclement() {
        var sighting = sighting(100,0.2,0);
        assertEquals(Pattern.STANDARD,SwarmAdaptiveTacticsPolicy.choose(
                sighting,100,1.0,List.of(),true).pattern());
        assertEquals(Pattern.STANDARD,SwarmAdaptiveTacticsPolicy.choose(
                sighting,100,1.0,List.of(new Vec2(2,1)),true).pattern());
        assertEquals(Pattern.STANDARD,SwarmAdaptiveTacticsPolicy.choose(
                sighting,100,1.0,GROUP,false).pattern());
    }

    @Test void staleOrUncertainSightingCannotUseExactMotionAsLiveKnowledge() {
        var sighting = sighting(100,0.2,0);
        assertEquals(Pattern.STANDARD,SwarmAdaptiveTacticsPolicy.choose(
                sighting,113,1.0,GROUP,true).pattern());
        assertEquals(Pattern.STANDARD,SwarmAdaptiveTacticsPolicy.choose(
                sighting,100,0.5,GROUP,true).pattern());
        assertEquals(Pattern.STANDARD,SwarmAdaptiveTacticsPolicy.choose(
                sighting,99,1.0,GROUP,true).pattern());
    }

    @Test void unrealisticVelocityAndInvalidFacingFallBackSafely() {
        var fast = new TargetObservation(TARGET,100,0,64,0,
                Double.NaN,Double.NaN,4.0,0);
        var frame = SwarmAdaptiveTacticsPolicy.choose(fast,100,1,GROUP,true);
        assertEquals(Pattern.SURROUND,frame.pattern());
        var solo = SwarmAdaptiveTacticsPolicy.choose(fast,100,1,List.of(),true);
        assertEquals(Pattern.STANDARD,solo.pattern());
        assertEquals(new Vec2(0,1),solo.forward());
    }

    @Test void spiderFlanksLeadFurtherThanZombiesButNeverRangedOrCreepers() {
        var frame = SwarmAdaptiveTacticsPolicy.choose(
                sighting(100,0.25,0),100,1,GROUP,true);
        Vec2 base = new Vec2(-4,2);
        Vec2 spider = SwarmAdaptiveTacticsPolicy.refineDestination(
                frame,base,SwarmRole.FLANK_LEFT,SwarmAgentArchetype.FLANKER);
        Vec2 zombie = SwarmAdaptiveTacticsPolicy.refineDestination(
                frame,base,SwarmRole.FLANK_LEFT,SwarmAgentArchetype.ASSAULT);
        assertEquals(-2.0,spider.x(),1e-9);
        assertEquals(-3.0,zombie.x(),1e-9);
        for(var archetype:List.of(SwarmAgentArchetype.RANGED_SUPPORT,
                SwarmAgentArchetype.BREACHER)) {
            assertEquals(base,SwarmAdaptiveTacticsPolicy.refineDestination(
                    frame,base,SwarmRole.FLANK_LEFT,archetype));
        }
        assertEquals(base,SwarmAdaptiveTacticsPolicy.refineDestination(
                frame,base,SwarmRole.REAR_PRESSURE,SwarmAgentArchetype.ASSAULT));
    }

    @Test void realSurroundPlacesOnlyRearPressureZombieOnFarSideOfStationaryTarget() {
        var frame = SwarmAdaptiveTacticsPolicy.choose(
                sighting(100,0,0),100,1.0,GROUP,true);
        Vec2 old = new Vec2(0,-4);
        Vec2 destination = SwarmAdaptiveTacticsPolicy.farSideWaypoint(
                frame,old,new Vec2(0,0),6.0,
                SwarmRole.REAR_PRESSURE,SwarmAgentArchetype.ASSAULT);
        assertEquals(new Vec2(0,3),destination);
        assertEquals(old,SwarmAdaptiveTacticsPolicy.farSideWaypoint(
                frame,old,new Vec2(0,0),6.0,
                SwarmRole.CHASER,SwarmAgentArchetype.ASSAULT));
        assertEquals(old,SwarmAdaptiveTacticsPolicy.farSideWaypoint(
                frame,old,new Vec2(0,0),6.0,
                SwarmRole.REAR_PRESSURE,SwarmAgentArchetype.BREACHER));
    }

    @Test void stationaryFarSideWaypointIsBoundedAndDisabledForStaleStandard() {
        var surround = new SwarmAdaptiveTacticsPolicy.Frame(
                Pattern.SURROUND,new Vec2(0,1),0);
        var result = SwarmAdaptiveTacticsPolicy.farSideWaypoint(
                surround,new Vec2(1,-4),new Vec2(10,10),100,
                SwarmRole.REAR_PRESSURE,SwarmAgentArchetype.ASSAULT);
        assertEquals(new Vec2(10,13),result);
        var standard = new SwarmAdaptiveTacticsPolicy.Frame(
                Pattern.STANDARD,new Vec2(0,1),0);
        assertEquals(new Vec2(1,-4),SwarmAdaptiveTacticsPolicy.farSideWaypoint(
                standard,new Vec2(1,-4),new Vec2(10,10),100,
                SwarmRole.REAR_PRESSURE,SwarmAgentArchetype.ASSAULT));
    }

    @Test void boundedOffsetNeverExceedsTwoBlocks() {
        var frame = new SwarmAdaptiveTacticsPolicy.Frame(
                Pattern.SWEEP,new Vec2(1,0),1000);
        var result = SwarmAdaptiveTacticsPolicy.refineDestination(
                frame,new Vec2(0,0),SwarmRole.FLANK_RIGHT,SwarmAgentArchetype.FLANKER);
        assertEquals(2.0,result.x(),1e-9);
    }
}
