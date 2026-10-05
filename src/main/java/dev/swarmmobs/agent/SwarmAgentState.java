package dev.swarmmobs.agent;

import java.util.UUID;

public final class SwarmAgentState {
    private UUID targetId;
    private long lastTargetObservationTick = Long.MIN_VALUE;
    private long nextPlanTick;
    private boolean directObservation;
    private int neighborCount;
    private int formationSlot;
    private SwarmRole role = SwarmRole.CHASER;

    public UUID targetId() {
        return targetId;
    }

    public long lastTargetObservationTick() {
        return lastTargetObservationTick;
    }

    public long nextPlanTick() {
        return nextPlanTick;
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

    public void rememberTarget(UUID targetId, long gameTick, boolean directObservation) {
        this.targetId = targetId;
        this.lastTargetObservationTick = gameTick;
        this.directObservation = directObservation;
    }

    public void forgetTarget() {
        this.targetId = null;
        this.directObservation = false;
    }

    public void scheduleNextPlan(long gameTick, int intervalTicks, int entityId) {
        int interval = Math.max(1, intervalTicks);
        int stagger = Math.floorMod(entityId, interval);
        this.nextPlanTick = gameTick + interval + stagger;
    }

    public void updateLocalPlan(int neighborCount, int formationSlot, SwarmRole role) {
        this.neighborCount = neighborCount;
        this.formationSlot = formationSlot;
        this.role = role;
    }
}
