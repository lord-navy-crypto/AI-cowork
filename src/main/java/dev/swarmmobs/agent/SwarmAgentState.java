package dev.swarmmobs.agent;

import dev.swarmmobs.algorithm.SwarmCommunicationPolicy.TargetMessage;
import dev.swarmmobs.algorithm.TargetObservation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SwarmAgentState {
    private static final int MAX_PENDING_TARGET_MESSAGES = 32;
    private static final int MAX_DELIVERED_MESSAGE_KEYS = 128;
    private TargetObservation targetObservation;
    private long nextPlanTick;
    private boolean planningScheduleInitialized;
    private boolean directObservation;
    private int neighborCount;
    private int formationSlot;
    private boolean formationSlotInitialized;
    private int pendingFormationSlot = -1;
    private long pendingFormationSlotSinceTick = Long.MIN_VALUE;
    private long formationSlotSwitchCount;
    private SwarmRole role = SwarmRole.CHASER;
    private boolean roleInitialized;
    private SwarmRole pendingRole;
    private long pendingRoleSinceTick = Long.MIN_VALUE;
    private long roleReassignmentCount;
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
    private long sensingAcceptedObservations;
    private long sensingDroppedObservations;
    private double lastSensingNoiseMagnitude;

    private final List<TargetMessage> pendingTargetMessages = new ArrayList<>();
    private final LinkedHashMap<MessageSourceTargetKey, Long> latestDeliveredObservationBySource =
            new LinkedHashMap<>(16, 0.75F, true);
    private long communicationAcceptedMessages;
    private long communicationDeliveredMessages;
    private long communicationDroppedMessages;

    public UUID targetId() {
        return targetObservation == null ? null : targetObservation.targetId();
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

    public int neighborCount() {
        return neighborCount;
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

    public long sensingAcceptedObservations() {
        return sensingAcceptedObservations;
    }

    public long sensingDroppedObservations() {
        return sensingDroppedObservations;
    }

    public double lastSensingNoiseMagnitude() {
        return lastSensingNoiseMagnitude;
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

    public void forgetTarget() {
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
        this.behaviorMode = SwarmBehaviorMode.ENGAGE;
        this.searchRadius = 0.0;
        clearPredictionTelemetry();
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

    public void clearNavigationTelemetry() {
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
