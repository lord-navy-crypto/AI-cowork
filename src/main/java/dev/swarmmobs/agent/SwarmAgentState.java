package dev.swarmmobs.agent;

import java.util.UUID;

public final class SwarmAgentState {
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
