package dev.swarmmobs.agent;

import dev.swarmmobs.algorithm.SwarmCommunicationPolicy.TargetMessage;
import dev.swarmmobs.algorithm.TargetObservation;
import dev.swarmmobs.algorithm.SwarmEngagementPolicy;
import dev.swarmmobs.algorithm.SwarmSupportPositionPolicy;
import dev.swarmmobs.algorithm.SwarmGameHandoffTimeoutPolicy;
import dev.swarmmobs.algorithm.SwarmFailedWaypointMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SwarmAgentState {
    private static final int MAX_PENDING_TARGET_MESSAGES = 32;
    private static final int MAX_DELIVERED_MESSAGE_KEYS = 128;
    private TargetObservation targetObservation;
    private long nextPlanTick;
    private boolean planningScheduleInitialized;
    private boolean directObservation;
    // Runtime-only context: never persisted into a save or used to decide
    // whether a vanilla Mob may attack. The original target memory persists.
    private SwarmEngagementPolicy.Mode engagementMode = SwarmEngagementPolicy.Mode.WORK;
    private long lastCombatEvidenceTick = Long.MIN_VALUE;
    private long lastLostTargetTick = Long.MIN_VALUE;
    // Hysteresis and temporary work leases must not cross target boundaries.
    private UUID tacticalAssignmentTarget;
    private int supportPositionSide;
    private long supportPositionSwitches;
    private long supportPositionFeasibleSamples;
    private long supportPositionUnavailableSamples;
    private int tacticalPeerCount;
    private int tacticalBreacherCount;
    private int neighborCount;
    private int formationSlot;
    private boolean formationSlotInitialized;
    private int pendingFormationSlot = -1;
    private long pendingFormationSlotSinceTick = Long.MIN_VALUE;
    private long formationSlotSwitchCount;
    private SwarmRole role = SwarmRole.CHASER;
    // Runtime-only encounter geometry, used for inspection and acceptance.
    private String tacticalPattern = "STANDARD";
    private boolean searchRallyActive;
    private long searchRallyEpisodes;
    // Updated by local same-target sightings, consulted by vanilla bow handoff.
    private boolean bowLaneClear = true;
    // Minecraft Skeleton has a locally checked open square to regain distance.
    private boolean rangedSpacingActive;
    private long rangedSpacingEpisodes;
    private long rangedWaypointHoldSamples;
    private long rangedSpacingStartedAt = Long.MIN_VALUE;
    private long rangedSpacingRetryAfter = Long.MIN_VALUE;
    private long rangedSpacingFallbacks;
    private long rangedNoProgressFallbacks;
    // A failed, already-tested Minecraft destination is not automatically
    // a good destination again on the very next game tick.
    private final SwarmFailedWaypointMemory rangedFailedWaypoints =
            new SwarmFailedWaypointMemory();
    private long zombieFlankStartedAt = Long.MIN_VALUE;
    private long zombieFlankRetryAfter = Long.MIN_VALUE;
    private long zombieFlankFallbacks;
    private long zombieNoProgressFallbacks;
    private boolean zombieShortFlankActive;
    private final SwarmFailedWaypointMemory zombieFailedWaypoints =
            new SwarmFailedWaypointMemory();
    // Local crowd-avoidance lane; reset on target switches.
    private int crowdLaneSide;
    private long crowdLaneLastSwitchTick = Long.MIN_VALUE;
    private long crowdLaneUses;
    private long crowdLaneRejected;
    private boolean roleInitialized;
    private SwarmRole pendingRole;
    private long pendingRoleSinceTick = Long.MIN_VALUE;
    private long roleReassignmentCount;
    private boolean coveringVacantFlank;
    private long vacantFlankCoverageEpisodes;
    private SwarmBehaviorMode behaviorMode = SwarmBehaviorMode.ENGAGE;
    private double searchRadius;
    private boolean hasPrediction;
    private double predictedTargetX;
    private double predictedTargetZ;
    private double predictionOffset;
    private boolean hasDestination;
    private double destinationX;
    private double destinationZ;
    private double separationMagnitude;
    private double cohesionMagnitude;
    private double alignmentMagnitude;
    private double steeringMagnitude;
    private SwarmNavigationMode navigationMode = SwarmNavigationMode.PLAN;
    private boolean hasNavigationWaypoint;
    private double navigationWaypointX;
    private double navigationWaypointZ;
    private long obstacleDetourCount;
    private long recoveryCount;
    private long recoveryPlanningAttempts;
    private long recoveryPlanningFailures;
    // Number of active movement episodes invalidated by changed game context.
    private long navigationEpisodeResets;
    private long navigationCommandRejections;
    private long skeletonPathRejectFallbacks;
    private long zombiePathRejectFallbacks;
    private SwarmPlannerContext plannerContext = SwarmPlannerContext.NONE;
    private int plannerCandidateCount;
    private int plannerBlockedCount;
    private int plannerUnreachableCount;
    private int plannerFeasibleCount;
    private int plannerSelectedIndex = -1;
    private double plannerSelectedScore;
    private long plannerPathQueryCount;
    private long sensingAcceptedObservations;
    private long sensingDroppedObservations;
    private double lastSensingNoiseMagnitude;
    private boolean searchEpisodeActive;
    private UUID searchEpisodeTargetId;
    private long searchEpisodeStartTick = Long.MIN_VALUE;
    private long searchEpisodesStarted;
    private long searchEpisodesSucceeded;
    private long searchEpisodesFailed;
    private long searchReacquisitionTicksTotal;
    private long lastSearchReacquisitionTicks;
    private BlockState carriedEngineeringBlock;
    private int carriedEngineeringBlockCount;
    private long engineeringBlocksBroken;
    private long engineeringBlocksPlaced;
    private SwarmEngineeringTask engineeringRequest;
    private SwarmEngineeringTask claimedEngineeringTask;
    private long engineeringRequestsPublished;
    private long engineeringTasksClaimed;
    private long engineeringTasksCompleted;
    private long engineeringMaterialsGiven;
    private long engineeringMaterialsReceived;
    private long nestsFounded;

    private SwarmTaskType currentTask = SwarmTaskType.RESERVE;
    private SwarmSpecialization specialization = SwarmSpecialization.RESERVE;
    private long specializationSinceTick = Long.MIN_VALUE;
    private long specializationSwitchCount;
    private final EnumMap<SwarmTaskType, Double> taskExperience =
            new EnumMap<>(SwarmTaskType.class);

    private final List<TargetMessage> pendingTargetMessages = new ArrayList<>();
    private final LinkedHashMap<MessageSourceTargetKey, Long> latestDeliveredObservationBySource =
            new LinkedHashMap<>(16, 0.75F, true);
    private long communicationAcceptedMessages;
    private long communicationDeliveredMessages;
    private long communicationDroppedMessages;

    public UUID targetId() {
        return targetObservation == null ? null : targetObservation.targetId();
    }

    public UUID tacticalAssignmentTarget() {
        return tacticalAssignmentTarget;
    }

    /**
     * Bind tactical hysteresis to the selected target (not the latest message
     * source). A new target is a new squad: stale slots/roles/specialization
     * must not hold for 12-30 ticks before the new squad can coordinate.
     * Task experience and cumulative counters deliberately survive switches.
     */
    public boolean bindTacticalTarget(UUID selectedTarget) {
        if (Objects.equals(tacticalAssignmentTarget, selectedTarget)) {
            return false;
        }
        tacticalAssignmentTarget = selectedTarget;
        resetSupportPositionState();
        tacticalPattern = "STANDARD";
        searchRallyActive = false;
        coveringVacantFlank = false;
        bowLaneClear = true;
        rangedSpacingActive = false;
        rangedSpacingStartedAt = Long.MIN_VALUE;
        rangedSpacingRetryAfter = Long.MIN_VALUE;
        rangedFailedWaypoints.clear();
        zombieFlankStartedAt = Long.MIN_VALUE;
        zombieFlankRetryAfter = Long.MIN_VALUE;
        zombieShortFlankActive = false;
        zombieFailedWaypoints.clear();
        crowdLaneSide = 0;
        crowdLaneLastSwitchTick = Long.MIN_VALUE;

        formationSlotInitialized = false;
        pendingFormationSlot = -1;
        pendingFormationSlotSinceTick = Long.MIN_VALUE;
        roleInitialized = false;
        pendingRole = null;
        pendingRoleSinceTick = Long.MIN_VALUE;
        resetActiveSpecialization();
        updateTacticalSquadTelemetry(0, 0);
        return true;
    }


    public long lastTargetObservationTick() {
        return targetObservation == null ? Long.MIN_VALUE : targetObservation.observationTick();
    }

    public TargetObservation targetObservation() {
        return targetObservation;
    }

    public boolean hasTargetObservation() {
        return targetObservation != null && targetObservation.hasFinitePosition();
    }

    public double targetConfidence(long currentTick, int memoryTicks) {
        return targetObservation == null ? 0.0 : targetObservation.confidence(currentTick, memoryTicks);
    }

    public long nextPlanTick() {
        return nextPlanTick;
    }

    public boolean planningScheduleInitialized() {
        return planningScheduleInitialized;
    }

    public boolean directObservation() {
        return directObservation;
    }

    public SwarmEngagementPolicy.Mode engagementMode() {
        return engagementMode;
    }

    public int supportPositionSide() {
        return supportPositionSide;
    }

    public long supportPositionSwitches() {
        return supportPositionSwitches;
    }

    public long supportPositionFeasibleSamples() {
        return supportPositionFeasibleSamples;
    }

    public long supportPositionUnavailableSamples() {
        return supportPositionUnavailableSamples;
    }

    /**
     * Store the selected side for a real same-target support-position
     * decision. Existing vanilla AI and navigator remain authoritative.
     */
    public void acceptSupportPositionDecision(
            SwarmSupportPositionPolicy.SupportDecision decision) {
        if (decision == null || decision.side() == 0) {
            supportPositionSide = 0;
            supportPositionUnavailableSamples++;
            return;
        }
        if (supportPositionSide != 0 && supportPositionSide != decision.side()) {
            supportPositionSwitches++;
        }
        supportPositionSide = decision.side();
        supportPositionFeasibleSamples++;
    }

    /** Clear temporary lane memory when the optimizer is not applicable. */
    public void clearSupportPositionSide() {
        supportPositionSide = 0;
    }

    /** A new target must not inherit the previous target's lane/counters. */
    public void resetSupportPositionState() {
        supportPositionSide = 0;
        supportPositionSwitches = 0;
        supportPositionFeasibleSamples = 0;
        supportPositionUnavailableSamples = 0;
    }

    /**
     * Called once per staggered planner sample. Direct observation or
     * fresh same-target squad combat enters COMBAT. Relay alone enters ALERT.
     * A brief RECOVERY holds off nest work after target evidence disappears.
     */
    public void updateEngagement(boolean aware, boolean combatEvidence, long now) {
        if (now < 0) return;
        if (aware && combatEvidence) lastCombatEvidenceTick = now;
        if (!aware && engagementMode != SwarmEngagementPolicy.Mode.RECOVERY
                && engagementMode != SwarmEngagementPolicy.Mode.WORK) {
            lastLostTargetTick = now;
        }
        long sinceDirect = lastCombatEvidenceTick == Long.MIN_VALUE
                ? Long.MAX_VALUE : Math.max(0L,now-lastCombatEvidenceTick);
        long sinceLost = lastLostTargetTick == Long.MIN_VALUE
                ? Long.MAX_VALUE : Math.max(0L,now-lastLostTargetTick);
        engagementMode = SwarmEngagementPolicy.next(
                engagementMode, aware, combatEvidence, sinceDirect, sinceLost);
        if (aware) lastLostTargetTick = Long.MIN_VALUE;
        if (engagementMode == SwarmEngagementPolicy.Mode.WORK) {
            lastCombatEvidenceTick = Long.MIN_VALUE;
            lastLostTargetTick = Long.MIN_VALUE;
        }
    }

    public boolean searchEpisodeActive() {
        return searchEpisodeActive;
    }

    public long searchEpisodesStarted() {
        return searchEpisodesStarted;
    }

    public long searchEpisodesSucceeded() {
        return searchEpisodesSucceeded;
    }

    public long searchEpisodesFailed() {
        return searchEpisodesFailed;
    }

    public long searchReacquisitionTicksTotal() {
        return searchReacquisitionTicksTotal;
    }

    public long lastSearchReacquisitionTicks() {
        return lastSearchReacquisitionTicks;
    }

    public int neighborCount() {
        return neighborCount;
    }
    
    /** Local gameplay formation mode, derived only from known observations. */
    public String tacticalPattern() {
        return tacticalPattern;
    }

    public void setTacticalPattern(String pattern) {
        tacticalPattern = pattern == null ? "STANDARD" : pattern;
    }

    /** Snapshot from the last local combat plan; not a raycast every tick. */
    public boolean bowLaneClear() {
        return bowLaneClear;
    }

    public void setBowLaneClear(boolean clear) {
        bowLaneClear = clear;
    }

    /** Same-target nearby members, excluding self. */
    public boolean rangedSpacingActive() {
        return rangedSpacingActive;
    }

    public long rangedSpacingEpisodes() {
        return rangedSpacingEpisodes;
    }

    public long rangedWaypointHoldSamples() {
        return rangedWaypointHoldSamples;
    }

    /** Increment only if a verified old game waypoint is actually reused. */
    public void recordRangedWaypointHold() {
        rangedWaypointHoldSamples++;
    }

    /** Count genuine starts. Without a game tick this is telemetry-only. */
    public void updateRangedSpacing(boolean active) {
        updateRangedSpacing(active, Long.MIN_VALUE);
    }

    public void updateRangedSpacing(boolean active, long tick) {
        if (active && !mayAttemptRangedSpacing(tick)) active = false;
        if (active && !rangedSpacingActive) {
            rangedSpacingEpisodes++;
            rangedSpacingStartedAt = tick;
        }
        if (!active) rangedSpacingStartedAt = Long.MIN_VALUE;
        rangedSpacingActive = active;
    }

    public boolean mayAttemptRangedSpacing(long tick) {
        return tick < 0 || tick >= rangedSpacingRetryAfter;
    }

    public long rangedSpacingFallbacks() {
        return rangedSpacingFallbacks;
    }

    public long rangedNoProgressFallbacks() {
        return rangedNoProgressFallbacks;
    }

    /**
     * An alternative Skeleton game square must pass both global retry
     * cooldown and the per-target recently failed waypoint check.
     * Rejecting a candidate has no side effects: UI counters remain real.
     */
    public boolean canUseRangedWaypoint(double x, double z, long tick) {
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || !mayAttemptRangedSpacing(tick)) {
            return false;
        }
        return rangedFailedWaypoints.allows(x, z, tick, 2.0);
    }

    public boolean hasRecentlyFailedRangedWaypoint(long tick) {
        return rangedFailedWaypoints.hasActive(tick);
    }

    public int recentRangedFailureLocations(long tick) {
        return rangedFailedWaypoints.activeCount(tick);
    }

    /**
     * A Skeleton reposition which lasts too long yields to native bow
     * controls instead of locking MOVE on an unreachable game square.
     */
    public boolean expireRangedSpacing(long tick) {
        if (!rangedSpacingActive || !SwarmGameHandoffTimeoutPolicy.timedOut(
                tick, rangedSpacingStartedAt,
                SwarmGameHandoffTimeoutPolicy.SKELETON_MAX_MOVE_TICKS)) {
            return false;
        }
        finishFailedRangedSpacing(tick);
        return true;
    }

    /** Report a measured in-game waypoint stall, not merely a timer expiry. */
    public boolean failRangedSpacingForNoProgress(long tick) {
        if (!rangedSpacingActive || tick < 0) return false;
        finishFailedRangedSpacing(tick);
        rangedNoProgressFallbacks++;
        return true;
    }

    /** An outright game path rejection is distinct from measured stalling. */
    public boolean failRangedSpacingForRejectedPath(long tick) {
        if (!rangedSpacingActive || tick < 0) return false;
        finishFailedRangedSpacing(tick);
        skeletonPathRejectFallbacks++;
        return true;
    }

    private void finishFailedRangedSpacing(long tick) {
        rangedSpacingActive = false;
        rangedSpacingStartedAt = Long.MIN_VALUE;
        rangedSpacingRetryAfter = SwarmGameHandoffTimeoutPolicy.nextEligibleTick(
                tick, SwarmGameHandoffTimeoutPolicy.SKELETON_RETRY_COOLDOWN_TICKS);
        if (hasDestination && Double.isFinite(destinationX)
                && Double.isFinite(destinationZ)) {
            rangedFailedWaypoints.record(destinationX, destinationZ, tick,
                    180, 2.0);
        }
        rangedSpacingFallbacks++;
    }

    /**
     * An optional Zombie flank may not retry its own recent failed square
     * merely because the shorter retry cooldown ended.
     */
    public boolean canUseZombieFlankWaypoint(double x, double z, long tick) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || tick < 0
                || tick < zombieFlankRetryAfter) {
            return false;
        }
        return zombieFailedWaypoints.allows(x, z, tick, 1.5);
    }

    public boolean hasRecentlyFailedZombieWaypoint(long tick) {
        return zombieFailedWaypoints.hasActive(tick);
    }

    public int recentZombieFailureLocations(long tick) {
        return zombieFailedWaypoints.activeCount(tick);
    }

    /** Called only when a visible game Zombie flank waypoint is worthwhile. */
    public boolean allowShortZombieFlank(long tick) {
        if (tick < 0 || tick < zombieFlankRetryAfter) {
            zombieShortFlankActive = false;
            return false;
        }
        if (zombieFlankStartedAt == Long.MIN_VALUE) zombieFlankStartedAt = tick;
        if (SwarmGameHandoffTimeoutPolicy.timedOut(tick,
                zombieFlankStartedAt,
                SwarmGameHandoffTimeoutPolicy.ZOMBIE_MAX_FLANK_TICKS)) {
            finishFailedShortZombieFlank(tick);
            return false;
        }
        zombieShortFlankActive = true;
        return true;
    }

    public boolean shortZombieFlankActive() {
        return zombieShortFlankActive;
    }

    public long zombieNoProgressFallbacks() {
        return zombieNoProgressFallbacks;
    }

    public boolean failShortZombieFlankForNoProgress(long tick) {
        if (!zombieShortFlankActive || tick < 0) return false;
        finishFailedShortZombieFlank(tick);
        zombieNoProgressFallbacks++;
        return true;
    }

    /** A denied real game path releases short flank without fake progress. */
    public boolean failShortZombieFlankForRejectedPath(long tick) {
        if (!zombieShortFlankActive || tick < 0) return false;
        finishFailedShortZombieFlank(tick);
        zombiePathRejectFallbacks++;
        return true;
    }

    private void finishFailedShortZombieFlank(long tick) {
            zombieShortFlankActive = false;
            zombieFlankStartedAt = Long.MIN_VALUE;
            zombieFlankRetryAfter = SwarmGameHandoffTimeoutPolicy.nextEligibleTick(
                    tick, SwarmGameHandoffTimeoutPolicy.ZOMBIE_RETRY_COOLDOWN_TICKS);
            if (hasDestination && Double.isFinite(destinationX)
                    && Double.isFinite(destinationZ)) {
                zombieFailedWaypoints.record(destinationX, destinationZ, tick,
                        150, 1.5);
            }
            zombieFlankFallbacks++;
    }

    public void clearShortZombieFlank() {
        zombieShortFlankActive = false;
        zombieFlankStartedAt = Long.MIN_VALUE;
    }

    public long zombieFlankFallbacks() {
        return zombieFlankFallbacks;
    }

    public int crowdLaneSide() {
        return crowdLaneSide;
    }

    public long crowdLaneUses() {
        return crowdLaneUses;
    }

    public long crowdLaneRejected() {
        return crowdLaneRejected;
    }

    public void recordCrowdLaneRejected() {
        crowdLaneRejected++;
    }

    /** Keep a lane for 20 ticks, except when its game waypoint is obstructed. */
    public int acceptCrowdLane(int proposed, long tick) {
        return acceptCrowdLane(proposed, tick, false);
    }

    public int acceptCrowdLane(int proposed, long tick, boolean oldLaneBlocked) {
        if (proposed == 0) {
            crowdLaneSide = 0;
            crowdLaneLastSwitchTick = Long.MIN_VALUE;
            return 0;
        }
        int next = Integer.compare(proposed, 0);
        if (crowdLaneSide == 0
                || oldLaneBlocked
                || (next != crowdLaneSide && tick >= crowdLaneLastSwitchTick
                    && tick - crowdLaneLastSwitchTick >= 20)) {
            crowdLaneSide = next;
            crowdLaneLastSwitchTick = tick;
        }
        crowdLaneUses++;
        return crowdLaneSide;
    }

    public boolean searchRallyActive() {
        return searchRallyActive;
    }

    public long searchRallyEpisodes() {
        return searchRallyEpisodes;
    }

    /** Count transitions, not planner frames, to avoid inflated telemetry. */
    public void updateSearchRally(boolean regrouping) {
        if (regrouping && !searchRallyActive) searchRallyEpisodes++;
        searchRallyActive = regrouping;
    }

    /** Current real role coverage, not a role computed for a UI preview. */
    public boolean coveringVacantFlank() {
        return coveringVacantFlank;
    }

    public long vacantFlankCoverageEpisodes() {
        return vacantFlankCoverageEpisodes;
    }

    public void updateVacantFlankCoverage(boolean active) {
        if (active && !coveringVacantFlank) vacantFlankCoverageEpisodes++;
        coveringVacantFlank = active;
    }

    public int tacticalPeerCount() {
        return tacticalPeerCount;
    }

    /** Same-target nearby Creeper breachers, excluding self. */
    public int tacticalBreacherCount() {
        return tacticalBreacherCount;
    }

    public void updateTacticalSquadTelemetry(int peers, int breachers) {
        this.tacticalPeerCount = Math.max(0, peers);
        this.tacticalBreacherCount = Math.max(0, Math.min(this.tacticalPeerCount, breachers));
    }


    public int formationSlot() {
        return formationSlot;
    }

    public int pendingFormationSlot() {
        return pendingFormationSlot;
    }

    public long formationSlotSwitchCount() {
        return formationSlotSwitchCount;
    }

    public SwarmTaskType currentTask() {
        return currentTask;
    }

    public SwarmSpecialization specialization() {
        return specialization;
    }

    public long specializationSinceTick() {
        return specializationSinceTick;
    }

    public long specializationSwitchCount() {
        return specializationSwitchCount;
    }

    public double taskExperience(SwarmTaskType task) {
        if (task == null) {
            return 0.0;
        }
        return taskExperience.getOrDefault(task, 0.0);
    }

    public SwarmSpecialization stabilizeSpecialization(
            SwarmTaskType candidateTask,
            SwarmSpecialization candidateSpecialization,
            long gameTick,
            int minHoldTicks
    ) {
        SwarmTaskType nextTask = candidateTask == null
                ? SwarmTaskType.RESERVE
                : candidateTask;
        SwarmSpecialization nextSpecialization = candidateSpecialization == null
                ? SwarmSpecialization.RESERVE
                : candidateSpecialization;
        int hold = Math.max(0, minHoldTicks);

        if (specializationSinceTick == Long.MIN_VALUE) {
            currentTask = nextTask;
            specialization = nextSpecialization;
            specializationSinceTick = gameTick;
            return specialization;
        }

        if (nextTask == currentTask && nextSpecialization == specialization) {
            return specialization;
        }

        if (gameTick - specializationSinceTick < hold) {
            return specialization;
        }

        currentTask = nextTask;
        specialization = nextSpecialization;
        specializationSinceTick = gameTick;
        specializationSwitchCount++;
        return specialization;
    }

    public void resetActiveSpecialization() {
        currentTask = SwarmTaskType.RESERVE;
        specialization = SwarmSpecialization.RESERVE;
        specializationSinceTick = Long.MIN_VALUE;
    }

    public void updateTaskExperience(
            SwarmTaskType activeTask,
            double gain,
            double decay
    ) {
        double retention = Math.max(0.0, Math.min(1.0, decay));
        for (SwarmTaskType task : SwarmTaskType.values()) {
            double value = taskExperience(task) * retention;
            if (task == activeTask) {
                value += Math.max(0.0, gain);
            }
            taskExperience.put(task, Math.max(0.0, Math.min(1.0, value)));
        }
    }

    public SwarmRole role() {
        return role;
    }

    public SwarmRole pendingRole() {
        return pendingRole;
    }

    public long roleReassignmentCount() {
        return roleReassignmentCount;
    }

    public SwarmBehaviorMode behaviorMode() {
        return behaviorMode;
    }

    public double searchRadius() {
        return searchRadius;
    }

    public boolean hasPrediction() {
        return hasPrediction;
    }

    public double predictedTargetX() {
        return predictedTargetX;
    }

    public double predictedTargetZ() {
        return predictedTargetZ;
    }

    public double predictionOffset() {
        return predictionOffset;
    }

    public boolean hasDestination() {
        return hasDestination;
    }

    public double destinationX() {
        return destinationX;
    }

    public double destinationZ() {
        return destinationZ;
    }

    public double separationMagnitude() {
        return separationMagnitude;
    }

    public double cohesionMagnitude() {
        return cohesionMagnitude;
    }

    public double alignmentMagnitude() {
        return alignmentMagnitude;
    }

    public double steeringMagnitude() {
        return steeringMagnitude;
    }

    public SwarmNavigationMode navigationMode() {
        return navigationMode;
    }

    public boolean hasNavigationWaypoint() {
        return hasNavigationWaypoint;
    }

    public double navigationWaypointX() {
        return navigationWaypointX;
    }

    public double navigationWaypointZ() {
        return navigationWaypointZ;
    }

    public long obstacleDetourCount() {
        return obstacleDetourCount;
    }

    public long recoveryCount() {
        return recoveryCount;
    }

    public long recoveryPlanningAttempts() {
        return recoveryPlanningAttempts;
    }

    public long recoveryPlanningFailures() {
        return recoveryPlanningFailures;
    }

    public long navigationEpisodeResets() {
        return navigationEpisodeResets;
    }

    public long navigationCommandRejections() {
        return navigationCommandRejections;
    }

    public void recordNavigationCommandRejection() {
        navigationCommandRejections++;
    }

    public long skeletonPathRejectFallbacks() {
        return skeletonPathRejectFallbacks;
    }

    public long zombiePathRejectFallbacks() {
        return zombiePathRejectFallbacks;
    }

    public void recordNavigationEpisodeReset() {
        navigationEpisodeResets++;
    }

    public SwarmPlannerContext plannerContext() {
        return plannerContext;
    }

    public int plannerCandidateCount() {
        return plannerCandidateCount;
    }

    public int plannerBlockedCount() {
        return plannerBlockedCount;
    }

    public int plannerUnreachableCount() {
        return plannerUnreachableCount;
    }

    public int plannerFeasibleCount() {
        return plannerFeasibleCount;
    }

    public int plannerSelectedIndex() {
        return plannerSelectedIndex;
    }

    public double plannerSelectedScore() {
        return plannerSelectedScore;
    }

    public long plannerPathQueryCount() {
        return plannerPathQueryCount;
    }

    public long sensingAcceptedObservations() {
        return sensingAcceptedObservations;
    }

    public long sensingDroppedObservations() {
        return sensingDroppedObservations;
    }

    public double lastSensingNoiseMagnitude() {
        return lastSensingNoiseMagnitude;
    }

    public BlockState carriedEngineeringBlock() {
        return carriedEngineeringBlock;
    }

    public SwarmEngineeringTask engineeringRequest(long gameTick) {
        if (engineeringRequest != null && !engineeringRequest.active(gameTick)) {
            engineeringRequest = null;
        }
        return engineeringRequest;
    }

    public SwarmEngineeringTask claimedEngineeringTask(long gameTick) {
        if (claimedEngineeringTask != null && !claimedEngineeringTask.active(gameTick)) {
            claimedEngineeringTask = null;
        }
        return claimedEngineeringTask;
    }

    public long engineeringRequestsPublished() {
        return engineeringRequestsPublished;
    }

    public long engineeringTasksClaimed() {
        return engineeringTasksClaimed;
    }

    public long engineeringTasksCompleted() {
        return engineeringTasksCompleted;
    }

    public long engineeringMaterialsGiven() {
        return engineeringMaterialsGiven;
    }

    public long engineeringMaterialsReceived() {
        return engineeringMaterialsReceived;
    }

    public long nestsFounded() {
        return nestsFounded;
    }

    public void recordNestFounded() {
        nestsFounded++;
    }

    public boolean transferOneEngineeringBlockTo(
            SwarmAgentState receiver,
            int receiverMaxCount
    ) {
        if (receiver == null
                || receiver == this
                || carriedEngineeringBlockCount <= 0
                || carriedEngineeringBlock == null
                || !receiver.canCarryEngineeringBlock(
                        carriedEngineeringBlock,
                        receiverMaxCount
                )) {
            return false;
        }

        BlockState material = carriedEngineeringBlock;
        carriedEngineeringBlockCount--;
        if (carriedEngineeringBlockCount == 0) {
            carriedEngineeringBlock = null;
        }

        if (receiver.carriedEngineeringBlockCount == 0) {
            receiver.carriedEngineeringBlock = material;
        }
        receiver.carriedEngineeringBlockCount++;

        engineeringMaterialsGiven++;
        receiver.engineeringMaterialsReceived++;
        return true;
    }



    public SwarmEngineeringTask publishEngineeringRequest(
            SwarmEngineeringTask.Type type,
            UUID requesterId,
            UUID claimantId,
            BlockPos position,
            long gameTick,
            int ttlTicks
    ) {
        engineeringRequest = new SwarmEngineeringTask(
                type,
                requesterId,
                claimantId,
                targetId(),
                position,
                gameTick,
                gameTick + Math.max(1, ttlTicks)
        );
        engineeringRequestsPublished++;
        return engineeringRequest;
    }

    public boolean acceptEngineeringTask(SwarmEngineeringTask task, long gameTick) {
        if (task == null || !task.active(gameTick)) {
            return false;
        }
        SwarmEngineeringTask previous = claimedEngineeringTask(gameTick);
        if (previous != null
                && previous.requesterId().equals(task.requesterId())
                && previous.createdTick() == task.createdTick()) {
            return true;
        }
        claimedEngineeringTask = task;
        engineeringTasksClaimed++;
        return true;
    }

    public void completeEngineeringTask(SwarmEngineeringTask task) {
        if (task == null) {
            return;
        }
        if (claimedEngineeringTask != null
                && claimedEngineeringTask.requesterId().equals(task.requesterId())
                && claimedEngineeringTask.createdTick() == task.createdTick()) {
            claimedEngineeringTask = null;
            engineeringTasksCompleted++;
        }
    }

    public void clearEngineeringRequestIfMatches(SwarmEngineeringTask task) {
        if (task == null || engineeringRequest == null) {
            return;
        }
        if (engineeringRequest.requesterId().equals(task.requesterId())
                && engineeringRequest.createdTick() == task.createdTick()) {
            engineeringRequest = null;
        }
    }

    public void clearClaimedEngineeringTaskIfMatches(SwarmEngineeringTask task) {
        if (task == null || claimedEngineeringTask == null) {
            return;
        }
        if (claimedEngineeringTask.requesterId().equals(task.requesterId())
                && claimedEngineeringTask.createdTick() == task.createdTick()) {
            claimedEngineeringTask = null;
        }
    }

    public void clearEngineeringCoordination() {
        engineeringRequest = null;
        claimedEngineeringTask = null;
    }

    public int carriedEngineeringBlockCount() {
        return carriedEngineeringBlockCount;
    }

    public long engineeringBlocksBroken() {
        return engineeringBlocksBroken;
    }

    public long engineeringBlocksPlaced() {
        return engineeringBlocksPlaced;
    }

    public boolean canCarryEngineeringBlock(BlockState state, int maxCount) {
        if (state == null || maxCount <= 0) {
            return false;
        }
        return carriedEngineeringBlockCount == 0
                || (carriedEngineeringBlock != null
                && carriedEngineeringBlock.is(state.getBlock())
                && carriedEngineeringBlockCount < maxCount);
    }

    public void recordEngineeringBlockBroken() {
        engineeringBlocksBroken++;
    }

    public boolean salvageEngineeringBlock(BlockState state, int maxCount) {
        if (!canCarryEngineeringBlock(state, maxCount)) {
            return false;
        }
        if (carriedEngineeringBlockCount == 0) {
            carriedEngineeringBlock = state;
        }
        carriedEngineeringBlockCount++;
        return true;
    }

    public boolean consumeEngineeringBlock() {
        if (carriedEngineeringBlockCount <= 0 || carriedEngineeringBlock == null) {
            return false;
        }
        carriedEngineeringBlockCount--;
        engineeringBlocksPlaced++;
        if (carriedEngineeringBlockCount == 0) {
            carriedEngineeringBlock = null;
        }
        return true;
    }

    public int pendingTargetMessageCount() {
        return pendingTargetMessages.size();
    }

    public long communicationAcceptedMessages() {
        return communicationAcceptedMessages;
    }

    public long communicationDeliveredMessages() {
        return communicationDeliveredMessages;
    }

    public long communicationDroppedMessages() {
        return communicationDroppedMessages;
    }

    public boolean enqueueTargetMessage(TargetMessage message) {
        if (message == null) {
            return false;
        }

        MessageSourceTargetKey key = new MessageSourceTargetKey(message.senderId(), message.targetId());
        Long latestDelivered = latestDeliveredObservationBySource.get(key);
        if (latestDelivered != null && message.observationTick() <= latestDelivered) {
            return false;
        }

        for (TargetMessage pending : pendingTargetMessages) {
            if (pending.senderId().equals(message.senderId())
                    && pending.targetId().equals(message.targetId())
                    && pending.observationTick() == message.observationTick()) {
                return false;
            }
        }

        if (pendingTargetMessages.size() >= MAX_PENDING_TARGET_MESSAGES) {
            pendingTargetMessages.remove(0);
            communicationDroppedMessages++;
        }

        pendingTargetMessages.add(message);
        communicationAcceptedMessages++;
        return true;
    }

    public List<TargetMessage> drainDeliverableTargetMessages(long currentTick) {
        List<TargetMessage> delivered = new ArrayList<>();
        Iterator<TargetMessage> iterator = pendingTargetMessages.iterator();

        while (iterator.hasNext()) {
            TargetMessage message = iterator.next();
            if (message.deliverTick() <= currentTick) {
                delivered.add(message);
                rememberDeliveredMessageVersion(message);
                iterator.remove();
            }
        }

        communicationDeliveredMessages += delivered.size();
        return delivered;
    }

    private void rememberDeliveredMessageVersion(TargetMessage message) {
        MessageSourceTargetKey key = new MessageSourceTargetKey(message.senderId(), message.targetId());
        latestDeliveredObservationBySource.merge(
                key,
                message.observationTick(),
                Math::max
        );

        while (latestDeliveredObservationBySource.size() > MAX_DELIVERED_MESSAGE_KEYS) {
            Iterator<Map.Entry<MessageSourceTargetKey, Long>> iterator =
                    latestDeliveredObservationBySource.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    public void recordCommunicationDrop() {
        communicationDroppedMessages++;
    }

    public void recordSensingAccepted(double noiseMagnitude) {
        sensingAcceptedObservations++;
        lastSensingNoiseMagnitude = Math.max(0.0, noiseMagnitude);
    }

    public void recordSensingDrop() {
        sensingDroppedObservations++;
        lastSensingNoiseMagnitude = 0.0;
    }

    public void clearPendingTargetMessages() {
        pendingTargetMessages.clear();
    }

    public void rememberTarget(TargetObservation observation, boolean directObservation) {
        this.targetObservation = observation;
        this.directObservation = directObservation;
    }

    public void rememberTarget(UUID targetId, long gameTick, boolean directObservation) {
        this.targetObservation = TargetObservation.unknownPosition(targetId, gameTick);
        this.directObservation = directObservation;
    }

    public void beginSearchEpisode(long gameTick, UUID targetId) {
        if (searchEpisodeActive) {
            return;
        }
        searchEpisodeActive = true;
        searchEpisodeTargetId = targetId;
        searchEpisodeStartTick = gameTick;
        searchEpisodesStarted++;
    }

    public void recordDirectReacquisition(long gameTick, UUID targetId) {
        if (!searchEpisodeActive) {
            return;
        }

        if (searchEpisodeTargetId != null && targetId != null && !searchEpisodeTargetId.equals(targetId)) {
            recordSearchFailure();
            return;
        }

        long elapsed = searchEpisodeStartTick == Long.MIN_VALUE
                ? 0L
                : Math.max(0L, gameTick - searchEpisodeStartTick);
        searchEpisodesSucceeded++;
        searchReacquisitionTicksTotal += elapsed;
        lastSearchReacquisitionTicks = elapsed;
        clearSearchEpisode();
    }

    public void recordSearchFailure() {
        if (!searchEpisodeActive) {
            return;
        }
        searchEpisodesFailed++;
        clearSearchEpisode();
    }

    private void clearSearchEpisode() {
        searchEpisodeActive = false;
        searchEpisodeTargetId = null;
        searchEpisodeStartTick = Long.MIN_VALUE;
    }

    public void forgetTarget() {
        updateSearchRally(false);
        bindTacticalTarget(null);
        this.targetObservation = null;
        this.directObservation = false;
        this.behaviorMode = SwarmBehaviorMode.ENGAGE;
        this.searchRadius = 0.0;
        clearPredictionTelemetry();
        this.hasDestination = false;
    }

    public void initializePlanSchedule(long gameTick, int intervalTicks, int entityId) {
        int interval = Math.max(1, intervalTicks);
        int stagger = Math.floorMod(entityId, interval);
        this.nextPlanTick = gameTick + stagger;
        this.planningScheduleInitialized = true;
    }

    public void scheduleNextPlan(long gameTick, int intervalTicks) {
        int interval = Math.max(1, intervalTicks);
        this.nextPlanTick = gameTick + interval;
        this.planningScheduleInitialized = true;
    }

    public int stabilizeFormationSlot(
            int candidateSlot,
            long gameTick,
            int hysteresisTicks
    ) {
        int candidate = Math.max(0, candidateSlot);
        int hold = Math.max(0, hysteresisTicks);

        if (!formationSlotInitialized) {
            formationSlot = candidate;
            formationSlotInitialized = true;
            pendingFormationSlot = -1;
            pendingFormationSlotSinceTick = Long.MIN_VALUE;
            return formationSlot;
        }

        if (candidate == formationSlot) {
            pendingFormationSlot = -1;
            pendingFormationSlotSinceTick = Long.MIN_VALUE;
            return formationSlot;
        }

        if (hold == 0) {
            formationSlot = candidate;
            formationSlotSwitchCount++;
            pendingFormationSlot = -1;
            pendingFormationSlotSinceTick = Long.MIN_VALUE;
            return formationSlot;
        }

        if (pendingFormationSlot != candidate) {
            pendingFormationSlot = candidate;
            pendingFormationSlotSinceTick = gameTick;
            return formationSlot;
        }

        if (gameTick - pendingFormationSlotSinceTick >= hold) {
            formationSlot = candidate;
            formationSlotSwitchCount++;
            pendingFormationSlot = -1;
            pendingFormationSlotSinceTick = Long.MIN_VALUE;
        }

        return formationSlot;
    }

    public SwarmRole stabilizeRole(
            SwarmRole candidateRole,
            long gameTick,
            int hysteresisTicks
    ) {
        SwarmRole candidate = candidateRole == null ? SwarmRole.CHASER : candidateRole;
        int hold = Math.max(0, hysteresisTicks);

        if (!roleInitialized) {
            role = candidate;
            roleInitialized = true;
            pendingRole = null;
            pendingRoleSinceTick = Long.MIN_VALUE;
            return role;
        }

        if (candidate == role) {
            pendingRole = null;
            pendingRoleSinceTick = Long.MIN_VALUE;
            return role;
        }

        if (hold == 0) {
            role = candidate;
            roleReassignmentCount++;
            pendingRole = null;
            pendingRoleSinceTick = Long.MIN_VALUE;
            return role;
        }

        if (pendingRole != candidate) {
            pendingRole = candidate;
            pendingRoleSinceTick = gameTick;
            return role;
        }

        if (gameTick - pendingRoleSinceTick >= hold) {
            role = candidate;
            roleReassignmentCount++;
            pendingRole = null;
            pendingRoleSinceTick = Long.MIN_VALUE;
        }

        return role;
    }

    public void clearLocalPlan(int neighborCount) {
        this.neighborCount = neighborCount;
        this.pendingRole = null;
        this.pendingRoleSinceTick = Long.MIN_VALUE;
        this.hasDestination = false;
        this.rangedSpacingActive = false;
        this.rangedSpacingStartedAt = Long.MIN_VALUE;
        clearShortZombieFlank();
        this.behaviorMode = SwarmBehaviorMode.ENGAGE;
        this.searchRadius = 0.0;
        clearPredictionTelemetry();
        clearNavigationTelemetry();
        this.separationMagnitude = 0.0;
        this.cohesionMagnitude = 0.0;
        this.alignmentMagnitude = 0.0;
        this.steeringMagnitude = 0.0;
    }

    public void updatePredictionTelemetry(
            double predictedTargetX,
            double predictedTargetZ,
            double predictionOffset
    ) {
        this.predictedTargetX = predictedTargetX;
        this.predictedTargetZ = predictedTargetZ;
        this.predictionOffset = Math.max(0.0, predictionOffset);
        this.hasPrediction = Double.isFinite(predictedTargetX)
                && Double.isFinite(predictedTargetZ);
    }

    public void updateNavigationTelemetry(
            SwarmNavigationMode mode,
            double waypointX,
            double waypointZ,
            boolean countTransition
    ) {
        SwarmNavigationMode next = mode == null ? SwarmNavigationMode.PLAN : mode;
        if (countTransition && next != this.navigationMode) {
            if (next == SwarmNavigationMode.OBSTACLE_DETOUR) {
                obstacleDetourCount++;
            } else if (next == SwarmNavigationMode.RECOVERY) {
                recoveryCount++;
            }
        }

        this.navigationMode = next;
        this.navigationWaypointX = waypointX;
        this.navigationWaypointZ = waypointZ;
        this.hasNavigationWaypoint = Double.isFinite(waypointX) && Double.isFinite(waypointZ);
    }

    public void recordRecoveryPlanning(boolean success) {
        recoveryPlanningAttempts++;
        if (!success) {
            recoveryPlanningFailures++;
        }
    }

    public void updatePlannerTelemetry(
            SwarmPlannerContext context,
            int candidateCount,
            int blockedCount,
            int unreachableCount,
            int feasibleCount,
            int selectedIndex,
            double selectedScore,
            long pathQueries
    ) {
        this.plannerContext = context == null ? SwarmPlannerContext.NONE : context;
        this.plannerCandidateCount = Math.max(0, candidateCount);
        this.plannerBlockedCount = Math.max(0, blockedCount);
        this.plannerUnreachableCount = Math.max(0, unreachableCount);
        this.plannerFeasibleCount = Math.max(0, feasibleCount);
        this.plannerSelectedIndex = selectedIndex;
        this.plannerSelectedScore = Double.isFinite(selectedScore) ? selectedScore : 0.0;
        this.plannerPathQueryCount += Math.max(0L, pathQueries);
    }

    public void clearPlannerTelemetry() {
        this.plannerContext = SwarmPlannerContext.NONE;
        this.plannerCandidateCount = 0;
        this.plannerBlockedCount = 0;
        this.plannerUnreachableCount = 0;
        this.plannerFeasibleCount = 0;
        this.plannerSelectedIndex = -1;
        this.plannerSelectedScore = 0.0;
    }

    public void clearNavigationTelemetry() {
        clearPlannerTelemetry();
        this.navigationMode = SwarmNavigationMode.PLAN;
        this.hasNavigationWaypoint = false;
        this.navigationWaypointX = 0.0;
        this.navigationWaypointZ = 0.0;
    }

    public void clearPredictionTelemetry() {
        this.hasPrediction = false;
        this.predictedTargetX = 0.0;
        this.predictedTargetZ = 0.0;
        this.predictionOffset = 0.0;
    }

    private record MessageSourceTargetKey(UUID senderId, UUID targetId) {}

    public void updateLocalPlan(
            int neighborCount,
            int formationSlot,
            SwarmRole role,
            double destinationX,
            double destinationZ,
            double separationMagnitude,
            double cohesionMagnitude
    ) {
        updateLocalPlan(
                neighborCount,
                formationSlot,
                role,
                destinationX,
                destinationZ,
                separationMagnitude,
                cohesionMagnitude,
                0.0,
                Math.hypot(separationMagnitude, cohesionMagnitude)
        );
    }

    public void updateLocalPlan(
            int neighborCount,
            int formationSlot,
            SwarmRole role,
            double destinationX,
            double destinationZ,
            double separationMagnitude,
            double cohesionMagnitude,
            double alignmentMagnitude,
            double steeringMagnitude
    ) {
        updateLocalPlan(
                neighborCount,
                formationSlot,
                role,
                SwarmBehaviorMode.ENGAGE,
                0.0,
                destinationX,
                destinationZ,
                separationMagnitude,
                cohesionMagnitude,
                alignmentMagnitude,
                steeringMagnitude
        );
    }

    public void updateLocalPlan(
            int neighborCount,
            int formationSlot,
            SwarmRole role,
            SwarmBehaviorMode behaviorMode,
            double searchRadius,
            double destinationX,
            double destinationZ,
            double separationMagnitude,
            double cohesionMagnitude,
            double alignmentMagnitude,
            double steeringMagnitude
    ) {
        this.neighborCount = neighborCount;
        this.formationSlot = formationSlot;
        this.role = role;
        this.behaviorMode = behaviorMode == null ? SwarmBehaviorMode.ENGAGE : behaviorMode;
        this.searchRadius = Math.max(0.0, searchRadius);
        this.destinationX = destinationX;
        this.destinationZ = destinationZ;
        this.separationMagnitude = separationMagnitude;
        this.cohesionMagnitude = cohesionMagnitude;
        this.alignmentMagnitude = alignmentMagnitude;
        this.steeringMagnitude = steeringMagnitude;
        this.hasDestination = true;
    }
}
