package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.agent.SwarmTaskType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmLocalRoleCoveragePolicyTest {
    private static final UUID FRONT = new UUID(0,1);
    private static final UUID LEFT_FILL = new UUID(0,2);
    private static final UUID RIGHT_FILL = new UUID(0,3);
    private static final UUID SPIDER = new UUID(0,4);

    private static SwarmLocalRoleCoveragePolicy.Member zombie(UUID id) {
        return new SwarmLocalRoleCoveragePolicy.Member(id,
                SwarmAgentArchetype.ASSAULT,SwarmRole.REAR_PRESSURE,
                SwarmTaskType.RESERVE);
    }

    private static SwarmLocalRoleCoveragePolicy.Member spider(SwarmRole role) {
        return new SwarmLocalRoleCoveragePolicy.Member(SPIDER,
                SwarmAgentArchetype.FLANKER,role,SwarmTaskType.FLANK);
    }

    private static SwarmLocalRoleCoveragePolicy.Coverage pick(UUID id,
            List<SwarmLocalRoleCoveragePolicy.Member> peers) {
        return SwarmLocalRoleCoveragePolicy.choose(id,
                SwarmAgentArchetype.ASSAULT,SwarmTaskType.RESERVE,
                SwarmRole.REAR_PRESSURE,peers,true);
    }

    @Test void threeAssaultMembersCoverFrontAndTwoMissingFlanks() {
        var p = pick(FRONT,List.of(zombie(LEFT_FILL),zombie(RIGHT_FILL)));
        var l = pick(LEFT_FILL,List.of(zombie(FRONT),zombie(RIGHT_FILL)));
        var r = pick(RIGHT_FILL,List.of(zombie(FRONT),zombie(LEFT_FILL)));
        assertEquals(SwarmRole.CHASER,p.role());
        assertEquals(SwarmRole.FLANK_LEFT,l.role());
        assertEquals(SwarmRole.FLANK_RIGHT,r.role());
        assertTrue(l.fillingMissingFlank());
        assertTrue(r.fillingMissingFlank());
        assertFalse(p.fillingMissingFlank());
    }

    @Test void oneObservedSpiderOnLeftLeadsOnlyToRightSideReplacement() {
        var group = List.of(zombie(FRONT),zombie(RIGHT_FILL),spider(SwarmRole.FLANK_LEFT));
        var replacement = pick(LEFT_FILL,group);
        assertEquals(SwarmRole.FLANK_RIGHT,replacement.role());
        assertTrue(replacement.fillingMissingFlank());
        assertEquals(SwarmRole.CHASER,pick(FRONT,
                List.of(zombie(LEFT_FILL),zombie(RIGHT_FILL),
                        spider(SwarmRole.FLANK_LEFT))).role());
    }

    @Test void oneObservedSpiderOnRightLeadsOnlyToLeftSideReplacement() {
        var replacement = pick(LEFT_FILL,List.of(zombie(FRONT),
                zombie(RIGHT_FILL),spider(SwarmRole.FLANK_RIGHT)));
        assertEquals(SwarmRole.FLANK_LEFT,replacement.role());
        assertTrue(replacement.fillingMissingFlank());
    }

    @Test void disappearanceOfSpiderReassignsMissingSideWithoutGlobalState() {
        var before = pick(LEFT_FILL,List.of(
                zombie(FRONT),zombie(RIGHT_FILL),spider(SwarmRole.FLANK_LEFT)));
        var after = pick(LEFT_FILL,List.of(zombie(FRONT),zombie(RIGHT_FILL)));
        assertEquals(SwarmRole.FLANK_RIGHT,before.role());
        assertEquals(SwarmRole.FLANK_LEFT,after.role());
    }

    @Test void twoDedicatedSpidersPreserveExistingWorkAndFormation() {
        UUID secondSpider = new UUID(0,5);
        var coverage = pick(LEFT_FILL,List.of(zombie(FRONT),
                spider(SwarmRole.FLANK_LEFT),
                new SwarmLocalRoleCoveragePolicy.Member(secondSpider,
                        SwarmAgentArchetype.FLANKER,SwarmRole.FLANK_RIGHT,
                        SwarmTaskType.FLANK)));
        assertEquals(SwarmRole.REAR_PRESSURE,coverage.role());
        assertFalse(coverage.fillingMissingFlank());
    }

    @Test void committedWorkersAreNotPromotedAndDoNotStealSlots() {
        UUID worker = new UUID(0,0);
        var busy = new SwarmLocalRoleCoveragePolicy.Member(worker,
                SwarmAgentArchetype.ASSAULT,SwarmRole.REAR_PRESSURE,
                SwarmTaskType.ENGINEERING);
        var candidate = pick(LEFT_FILL,List.of(
                busy,zombie(FRONT),zombie(RIGHT_FILL)));
        assertEquals(SwarmRole.FLANK_LEFT,candidate.role());
        var selfBusy = SwarmLocalRoleCoveragePolicy.choose(LEFT_FILL,
                SwarmAgentArchetype.ASSAULT,SwarmTaskType.MATERIAL,
                SwarmRole.REAR_PRESSURE,List.of(zombie(FRONT),zombie(RIGHT_FILL)),true);
        assertEquals(SwarmRole.REAR_PRESSURE,selfBusy.role());
        assertFalse(selfBusy.fillingMissingFlank());
    }

    @Test void isolatedZombieAndNonAssaultSpeciesAreNotForceReassigned() {
        assertEquals(SwarmRole.REAR_PRESSURE,
                pick(FRONT,List.of()).role());
        assertEquals(SwarmRole.REAR_PRESSURE,
                SwarmLocalRoleCoveragePolicy.choose(SPIDER,
                        SwarmAgentArchetype.FLANKER,SwarmTaskType.FLANK,
                        SwarmRole.REAR_PRESSURE,List.of(zombie(FRONT)),true).role());
    }

    @Test void sameRosterOrderAlwaysGivesSameSideAndDisabledReturnsBaseline() {
        var first=pick(RIGHT_FILL,List.of(zombie(FRONT),zombie(LEFT_FILL)));
        var shuffled=pick(RIGHT_FILL,List.of(zombie(LEFT_FILL),zombie(FRONT)));
        assertEquals(first,shuffled);
        var disabled=SwarmLocalRoleCoveragePolicy.choose(RIGHT_FILL,
                SwarmAgentArchetype.ASSAULT,SwarmTaskType.RESERVE,
                SwarmRole.REAR_PRESSURE,List.of(zombie(FRONT),zombie(LEFT_FILL)),
                false);
        assertEquals(SwarmRole.REAR_PRESSURE,disabled.role());
        assertFalse(disabled.fillingMissingFlank());
    }

    @Test void duplicatedMemberIdsDoNotCreatePhantomPersonnel() {
        var duplicate=pick(LEFT_FILL,List.of(zombie(FRONT),zombie(FRONT)));
        assertEquals(SwarmRole.FLANK_LEFT,duplicate.role());
        assertEquals(2,duplicate.availableAssaults());
    }
}
