package dev.swarmmobs.colony;

import dev.swarmmobs.algorithm.SwarmTaskBidPolicy;
import dev.swarmmobs.agent.SwarmTaskType;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmColonyEmergencePolicyTest {
    private static final UUID WORKER = UUID.fromString(
            "e6493fc3-7a9b-487f-9b6b-9937d4d431aa");
    private static final SwarmNestColonyPolicy.Kind FOOD =
            SwarmNestColonyPolicy.Kind.NUTRIENT;

    @Test
    void originalSwarmResponseThresholdIsUsedWithResourceScarcity() {
        double threshold=SwarmTaskBidPolicy.responseThreshold(
                WORKER,SwarmTaskType.MATERIAL,1.0);
        assertTrue(threshold>0.0 && threshold<1.0);
        double hungry=SwarmColonyEmergencePolicy.workCost(
                16,WORKER,FOOD,8,6,0,0,0,0,0);
        double fed=SwarmColonyEmergencePolicy.workCost(
                16,WORKER,FOOD,8,6,12,0,0,0,0);
        assertTrue(hungry<fed,"Higher demand must increase a worker's response");
        assertEquals(1.0,SwarmColonyEmergencePolicy.shortage(FOOD,8,6,0),1e-9);
        assertEquals(0.0,SwarmColonyEmergencePolicy.shortage(FOOD,8,6,12),1e-9);
    }

    @Test
    void pheromoneSupportsWorkButStopAndCongestionPreventLockIn() {
        double base=SwarmColonyEmergencePolicy.workCost(
                10,WORKER,FOOD,0,0,2,0,0,0,0);
        double source=SwarmColonyEmergencePolicy.workCost(
                10,WORKER,FOOD,0,0,2,4,0,0,0);
        double crowded=SwarmColonyEmergencePolicy.workCost(
                10,WORKER,FOOD,0,0,2,4,0,5,0);
        double stopped=SwarmColonyEmergencePolicy.workCost(
                10,WORKER,FOOD,0,0,2,4,4,0,0);
        double veteran=SwarmColonyEmergencePolicy.workCost(
                10,WORKER,FOOD,0,0,2,4,0,0,1);
        assertTrue(source<base);
        assertTrue(crowded>source);
        assertTrue(stopped>source);
        assertTrue(veteran<=source);
    }

    @Test
    void onlyUsefulFiniteAndBoundedSignalsAffectChoices() {
        assertEquals(Double.POSITIVE_INFINITY,SwarmColonyEmergencePolicy.workCost(
                3,WORKER,SwarmNestColonyPolicy.Kind.NONE,0,0,0,4,0,0,0));
        assertEquals(Double.POSITIVE_INFINITY,SwarmColonyEmergencePolicy.workCost(
                Double.NaN,WORKER,FOOD,0,0,0,0,0,0,0));
        double noScent=SwarmColonyEmergencePolicy.workCost(
                12,WORKER,FOOD,0,0,0,0,0,0,0);
        double invalid=SwarmColonyEmergencePolicy.workCost(
                12,WORKER,FOOD,0,0,0,Double.NaN,Double.NaN,0,0);
        assertEquals(noScent,invalid,1e-9);
        assertTrue(Double.isFinite(SwarmColonyEmergencePolicy.workCost(
                12,WORKER,FOOD,0,0,0,100,100,100,100)));
    }

    @Test
    void crowdingDownregulatesActualDepositsRatherThanAmplifyingThem() {
        assertEquals(1.0,SwarmColonyEmergencePolicy.depositionMultiplier(0),1e-9);
        assertTrue(SwarmColonyEmergencePolicy.depositionMultiplier(2)
                < SwarmColonyEmergencePolicy.depositionMultiplier(0));
        assertTrue(SwarmColonyEmergencePolicy.depositionMultiplier(8)
                < SwarmColonyEmergencePolicy.depositionMultiplier(2));
        assertEquals(SwarmColonyEmergencePolicy.depositionMultiplier(8),
                SwarmColonyEmergencePolicy.depositionMultiplier(800),1e-9);
    }

    @Test
    void stableMinorityScoutsExploreMoreIndependentlyWithoutRandomFlips() {
        int independent=0;
        for (long n=0;n<256;n++) {
            UUID worker=new UUID(n,Long.rotateLeft(n*883L,11));
            boolean explorer=SwarmColonyEmergencePolicy.isIndependentExplorer(worker);
            assertEquals(explorer,SwarmColonyEmergencePolicy.isIndependentExplorer(worker));
            if(explorer) {
                independent++;
                assertEquals(1.4,SwarmColonyEmergencePolicy.sensedAttraction(worker,4),1e-9);
            } else {
                assertEquals(4,SwarmColonyEmergencePolicy.sensedAttraction(worker,4),1e-9);
            }
        }
        assertTrue(independent>=10 && independent<=60,
                "Explorers should be a stable small subset, not every worker");
    }
}
