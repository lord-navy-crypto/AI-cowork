package dev.swarmmobs.agent;

import dev.swarmmobs.algorithm.SwarmCommunicationPolicy.TargetMessage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class SwarmAgentState {
    private static final int MAX_PENDING_TARGET_MESSAGES = 32;
    private UUID targetId;
    private long lastTargetObservationTick = Long.MIN_VALUE;
    private long nextPlanTick;
    private boolean planningScheduleInitialized;
    private boolean directObservation;
    private int neighborCount;
    private int formationSlot;
    private SwarmRole role = SwarmRole.CHASER;
    private boolean hasDestination;
    private double destinationX;
    private double destinationZ;
    private double separationMagnitude;
    private double cohesionMagnitude;

    private final List<TargetMessage> pendingTargetMessages = new ArrayList<>();
    private long communicationAcceptedMessages;
    private long communicationDeliveredMessages;
    private long communicationDroppedMessages;

    public UUID targetId() {
        return targetId;
    }

    public long lastTargetObservationTick() {
        return lastTargetObservationTick;
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

    public SwarmRole role() {
        return role;
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
                iterator.remove();
            }
        }

        communicationDeliveredMessages += delivered.size();
        return delivered;
    }

    public void recordCommunicationDrop() {
        communicationDroppedMessages++;
    }

    public void clearPendingTargetMessages() {
        pendingTargetMessages.clear();
    }

    public void rememberTarget(UUID targetId, long gameTick, boolean directObservation) {
        this.targetId = targetId;
        this.lastTargetObservationTick = gameTick;
        this.directObservation = directObservation;
    }

    public void forgetTarget() {
        this.targetId = null;
        this.directObservation = false;
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

    public void clearLocalPlan(int neighborCount) {
        this.neighborCount = neighborCount;
        this.formationSlot = 0;
        this.role = SwarmRole.CHASER;
        this.hasDestination = false;
        this.separationMagnitude = 0.0;
        this.cohesionMagnitude = 0.0;
    }

    public void updateLocalPlan(
            int neighborCount,
            int formationSlot,
            SwarmRole role,
            double destinationX,
            double destinationZ,
            double separationMagnitude,
            double cohesionMagnitude
    ) {
        this.neighborCount = neighborCount;
        this.formationSlot = formationSlot;
        this.role = role;
        this.destinationX = destinationX;
        this.destinationZ = destinationZ;
        this.separationMagnitude = separationMagnitude;
        this.cohesionMagnitude = cohesionMagnitude;
        this.hasDestination = true;
    }
}
