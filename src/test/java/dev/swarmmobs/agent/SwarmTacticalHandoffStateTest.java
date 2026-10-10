package dev.swarmmobs.agent;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SwarmTacticalHandoffStateTest {

    @Test void targetChangesResetTacticalPatternAndOldBowBlocker() {
        SwarmAgentState state = new SwarmAgentState();
        UUID alpha = new UUID(0, 11);
        UUID beta = new UUID(0, 12);
        state.bindTacticalTarget(alpha);
        state.setTacticalPattern("SWEEP");
        state.setBowLaneClear(false);

        assertEquals("SWEEP", state.tacticalPattern());
        assertFalse(state.bowLaneClear());
        assertFalse(state.bindTacticalTarget(alpha));
        assertFalse(state.bowLaneClear());

        assertTrue(state.bindTacticalTarget(beta));
        assertEquals("STANDARD", state.tacticalPattern());
        assertTrue(state.bowLaneClear());
    }

    @Test void crowdSideOnlySwitchesAfterMinimumHold() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(1,1));
        assertEquals(1,state.acceptCrowdLane(1,100));
        assertEquals(1,state.acceptCrowdLane(-1,107));
        assertEquals(1,state.acceptCrowdLane(-1,119));
        assertEquals(-1,state.acceptCrowdLane(-1,120));
        assertEquals(4,state.crowdLaneUses());
        assertEquals(0,state.acceptCrowdLane(0,121));
    }

    @Test void blockedOldRouteMaySwitchImmediatelyWithoutOscillation() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(2,1));
        assertEquals(1,state.acceptCrowdLane(1,100));
        // Ordinary density updates cannot thrash an active lane.
        assertEquals(1,state.acceptCrowdLane(-1,102));
        // An impassable actual route is a reason to override hysteresis.
        assertEquals(-1,state.acceptCrowdLane(-1,103,true));
        assertEquals(-1,state.acceptCrowdLane(1,104));
        state.recordCrowdLaneRejected();
        assertEquals(1,state.crowdLaneRejected());
        assertEquals(0,state.acceptCrowdLane(0,105));
        assertEquals(1,state.acceptCrowdLane(1,106));
    }

    @Test void newTargetResetsOldCrowdWaypoints() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(1,1));
        state.acceptCrowdLane(-1,300);
        state.bindTacticalTarget(new UUID(1,2));
        assertEquals(0,state.crowdLaneSide());
        assertEquals(1,state.acceptCrowdLane(1,301));
    }

    @Test void searchRallyCountsEpisodesNotPlannerTicks() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(5,1));
        state.updateSearchRally(true);
        state.updateSearchRally(true);
        assertEquals(1,state.searchRallyEpisodes());
        assertTrue(state.searchRallyActive());
        state.updateSearchRally(false);
        state.updateSearchRally(true);
        assertEquals(2,state.searchRallyEpisodes());
    }

    @Test void targetChangeClearsSearchRally() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(5,1));
        state.updateSearchRally(true);
        state.bindTacticalTarget(new UUID(5,2));
        assertFalse(state.searchRallyActive());
        assertEquals(1,state.searchRallyEpisodes());
        state.forgetTarget();
        assertFalse(state.searchRallyActive());
    }

    @Test void movementEpisodeResetTelemetryCountsOnlyExplicitInvalidations() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(8,1));
        assertEquals(0,state.navigationEpisodeResets());
        state.recordNavigationEpisodeReset();
        assertEquals(1,state.navigationEpisodeResets());
        state.bindTacticalTarget(new UUID(8,2));
        // Target changes alone must not invent an executed navigation reset.
        assertEquals(1,state.navigationEpisodeResets());
        state.recordNavigationEpisodeReset();
        assertEquals(2,state.navigationEpisodeResets());
    }

    @Test void missingFlankCoverageCountsRealReplacementEpisodes() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(0, 7));
        state.updateVacantFlankCoverage(true);
        state.updateVacantFlankCoverage(true);
        assertTrue(state.coveringVacantFlank());
        assertEquals(1, state.vacantFlankCoverageEpisodes());
        state.updateVacantFlankCoverage(false);
        assertFalse(state.coveringVacantFlank());
        state.updateVacantFlankCoverage(true);
        assertEquals(2, state.vacantFlankCoverageEpisodes());
    }

    @Test void targetChangeStopsOldFlankFillButKeepsHistoricalCount() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(0, 7));
        state.updateVacantFlankCoverage(true);
        state.bindTacticalTarget(new UUID(0, 8));
        assertFalse(state.coveringVacantFlank());
        assertEquals(1, state.vacantFlankCoverageEpisodes());
    }

    @Test void SkeletonMovementEpisodeCountersReflectActualRepositionStarts() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(2, 3));
        state.updateRangedSpacing(true);
        state.updateRangedSpacing(true);
        assertTrue(state.rangedSpacingActive());
        assertEquals(1, state.rangedSpacingEpisodes());
        state.updateRangedSpacing(false);
        state.updateRangedSpacing(true);
        assertEquals(2, state.rangedSpacingEpisodes());
        state.bindTacticalTarget(new UUID(2, 4));
        assertFalse(state.rangedSpacingActive());
        assertEquals(2, state.rangedSpacingEpisodes());
    }

    @Test void SkeletonTimedMoveReturnsBowControlAndHonorsCooldown() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(9, 1));
        state.updateRangedSpacing(true, 100);
        assertTrue(state.rangedSpacingActive());
        assertFalse(state.expireRangedSpacing(129));
        assertTrue(state.expireRangedSpacing(130));
        assertFalse(state.rangedSpacingActive());
        assertEquals(1,state.rangedSpacingFallbacks());
        state.updateRangedSpacing(true, 131);
        assertFalse(state.rangedSpacingActive());
        assertEquals(1,state.rangedSpacingEpisodes());
        state.updateRangedSpacing(true, 180);
        assertTrue(state.rangedSpacingActive());
        assertEquals(2,state.rangedSpacingEpisodes());
        state.bindTacticalTarget(new UUID(9, 2));
        assertFalse(state.rangedSpacingActive());
        assertTrue(state.mayAttemptRangedSpacing(1));
    }

    @Test void SkeletonRemembersTimedOutGameSquareButAcceptsAnother() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(10, 1));
        state.updateLocalPlan(0, 0, SwarmRole.REAR_PRESSURE,
                6.0, 9.0, 0, 0);
        state.updateRangedSpacing(true, 100);
        assertTrue(state.expireRangedSpacing(130));
        assertTrue(state.hasRecentlyFailedRangedWaypoint(180));
        // The global 50-tick cooldown still takes precedence.
        assertFalse(state.canUseRangedWaypoint(9, 9, 179));
        assertFalse(state.canUseRangedWaypoint(6, 9, 180));
        assertFalse(state.canUseRangedWaypoint(7, 9, 180));
        assertTrue(state.canUseRangedWaypoint(9, 9, 180));
        assertTrue(state.canUseRangedWaypoint(6, 9, 310));
        assertFalse(state.hasRecentlyFailedRangedWaypoint(310));
    }

    @Test void SkeletonFailedWaypointNeverLeaksToAnotherMinecraftTarget() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(10, 1));
        state.updateLocalPlan(0, 0, SwarmRole.REAR_PRESSURE,
                6.0, 9.0, 0, 0);
        state.updateRangedSpacing(true, 100);
        assertTrue(state.expireRangedSpacing(130));
        assertFalse(state.canUseRangedWaypoint(6, 9, 180));
        state.bindTacticalTarget(new UUID(10, 2));
        assertFalse(state.hasRecentlyFailedRangedWaypoint(181));
        assertTrue(state.canUseRangedWaypoint(6, 9, 181));
        assertFalse(state.canUseRangedWaypoint(Double.NaN, 9, 181));
    }

    @Test void SkeletonNoSuccessfulWaypointDoesNotFabricateFailureHistory() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(10, 3));
        state.updateRangedSpacing(true, 100);
        assertTrue(state.expireRangedSpacing(130));
        assertFalse(state.hasRecentlyFailedRangedWaypoint(180));
        assertTrue(state.canUseRangedWaypoint(6, 9, 180));
    }

    @Test void ZombieShortSideMovementGivesUpAndLetsMeleeResume() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(7,1));
        assertTrue(state.allowShortZombieFlank(100));
        assertTrue(state.allowShortZombieFlank(117));
        assertFalse(state.allowShortZombieFlank(118));
        assertEquals(1,state.zombieFlankFallbacks());
        assertFalse(state.allowShortZombieFlank(147));
        assertTrue(state.allowShortZombieFlank(148));
        state.clearShortZombieFlank();
        state.bindTacticalTarget(new UUID(7,2));
        assertTrue(state.allowShortZombieFlank(1));
    }

    @Test void ZombieTimedOutFlankAvoidsSameBlockedSquareButAcceptsAlternative() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(11, 1));
        state.updateLocalPlan(0, 0, SwarmRole.FLANK_LEFT, 4, 8, 0, 0);
        assertTrue(state.allowShortZombieFlank(100));
        assertFalse(state.allowShortZombieFlank(118));
        assertEquals(1, state.zombieFlankFallbacks());
        assertTrue(state.hasRecentlyFailedZombieWaypoint(148));
        assertFalse(state.canUseZombieFlankWaypoint(4, 8, 147));
        // Retry is permitted after cooldown, but not at the same failed spot.
        assertFalse(state.canUseZombieFlankWaypoint(4, 8, 148));
        assertTrue(state.canUseZombieFlankWaypoint(6, 8, 148));
        assertTrue(state.canUseZombieFlankWaypoint(4, 8, 268));
        assertFalse(state.hasRecentlyFailedZombieWaypoint(268));
        assertFalse(state.canUseZombieFlankWaypoint(Double.NaN, 8, 268));
    }

    @Test void ZombieFailureMemoryClearsForDifferentTarget() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(11, 1));
        state.updateLocalPlan(0, 0, SwarmRole.FLANK_RIGHT, 4, 8, 0, 0);
        assertTrue(state.allowShortZombieFlank(100));
        assertFalse(state.allowShortZombieFlank(118));
        state.bindTacticalTarget(new UUID(11, 2));
        assertFalse(state.hasRecentlyFailedZombieWaypoint(149));
        assertTrue(state.canUseZombieFlankWaypoint(4, 8, 149));
    }

    @Test void SkeletonMeasuredStallReusesFailedPositionCooldownAndCountsSeparately() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(30,1));
        state.updateLocalPlan(0,0,SwarmRole.REAR_PRESSURE,6,9,0,0);
        state.updateRangedSpacing(true,100);
        assertTrue(state.failRangedSpacingForNoProgress(112));
        assertEquals(1,state.rangedSpacingFallbacks());
        assertEquals(1,state.rangedNoProgressFallbacks());
        assertFalse(state.rangedSpacingActive());
        assertFalse(state.canUseRangedWaypoint(6,9,162));
        assertTrue(state.canUseRangedWaypoint(9,9,162));
        assertFalse(state.failRangedSpacingForNoProgress(113));
        assertEquals(1,state.rangedNoProgressFallbacks());
    }

    @Test void ZombieMeasuredStallResumesNativeMeleeAndNoRepeatedFallback() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(31,1));
        state.updateLocalPlan(0,0,SwarmRole.FLANK_LEFT,4,8,0,0);
        assertTrue(state.allowShortZombieFlank(100));
        assertTrue(state.shortZombieFlankActive());
        assertTrue(state.failShortZombieFlankForNoProgress(112));
        assertEquals(1,state.zombieFlankFallbacks());
        assertEquals(1,state.zombieNoProgressFallbacks());
        assertFalse(state.shortZombieFlankActive());
        assertFalse(state.allowShortZombieFlank(113));
        assertFalse(state.canUseZombieFlankWaypoint(4,8,142));
        assertTrue(state.canUseZombieFlankWaypoint(6,8,142));
        assertFalse(state.failShortZombieFlankForNoProgress(113));
        state.bindTacticalTarget(new UUID(31,2));
        assertTrue(state.allowShortZombieFlank(114));
        assertEquals(1,state.zombieNoProgressFallbacks());
    }

    @Test void NormalZombieFlankCompletionClearsInProgressStateWithoutFailing() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(32,1));
        assertTrue(state.allowShortZombieFlank(100));
        state.clearShortZombieFlank();
        assertFalse(state.shortZombieFlankActive());
        assertEquals(0,state.zombieNoProgressFallbacks());
    }

    @Test void SkeletonAvoidsTwoConsecutiveUnreachableGameLocations() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(40,1));
        state.updateLocalPlan(0,0,SwarmRole.REAR_PRESSURE,0,6,0,0);
        state.updateRangedSpacing(true,100);
        assertTrue(state.expireRangedSpacing(130)); // first failure
        state.updateLocalPlan(0,0,SwarmRole.REAR_PRESSURE,5,6,0,0);
        state.updateRangedSpacing(true,180);
        assertTrue(state.expireRangedSpacing(210)); // second failure
        assertEquals(2,state.recentRangedFailureLocations(260));
        assertFalse(state.canUseRangedWaypoint(0,6,260));
        assertFalse(state.canUseRangedWaypoint(5,6,260));
        assertTrue(state.canUseRangedWaypoint(10,6,260));
        state.bindTacticalTarget(new UUID(40,2));
        assertEquals(0,state.recentRangedFailureLocations(261));
        assertTrue(state.canUseRangedWaypoint(0,6,261));
    }

    @Test void ZombieAvoidsTwoFailedFlankSquaresWhileNativeMeleeStillAvailable() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(41,1));
        state.updateLocalPlan(0,0,SwarmRole.FLANK_LEFT,0,5,0,0);
        assertTrue(state.allowShortZombieFlank(100));
        assertFalse(state.allowShortZombieFlank(118));
        state.updateLocalPlan(0,0,SwarmRole.FLANK_RIGHT,4,5,0,0);
        assertTrue(state.allowShortZombieFlank(148));
        assertFalse(state.allowShortZombieFlank(166));
        assertEquals(2,state.recentZombieFailureLocations(196));
        assertFalse(state.canUseZombieFlankWaypoint(0,5,196));
        assertFalse(state.canUseZombieFlankWaypoint(4,5,196));
        assertTrue(state.canUseZombieFlankWaypoint(8,5,196));
        state.bindTacticalTarget(new UUID(41,2));
        assertEquals(0,state.recentZombieFailureLocations(197));
        assertTrue(state.canUseZombieFlankWaypoint(0,5,197));
    }

    @Test void clearingTargetDoesNotLeavePhantomSquadState() {
        SwarmAgentState state = new SwarmAgentState();
        state.bindTacticalTarget(new UUID(1, 2));
        state.setTacticalPattern("SURROUND");
        state.setBowLaneClear(false);
        state.bindTacticalTarget(null);
        assertEquals("STANDARD", state.tacticalPattern());
        assertTrue(state.bowLaneClear());
    }
}
