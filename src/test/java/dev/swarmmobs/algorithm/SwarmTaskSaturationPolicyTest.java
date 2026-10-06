package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmTaskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmTaskSaturationPolicyTest {

    @Test
    void highEngineeringDemandScalesDesiredWorkforceWithGroupSize() {
        assertEquals(1, SwarmTaskSaturationPolicy.desiredHeadcount(
                SwarmTaskType.ENGINEERING, 1.0, 4));
        assertEquals(2, SwarmTaskSaturationPolicy.desiredHeadcount(
                SwarmTaskType.ENGINEERING, 1.0, 10));
        assertEquals(4, SwarmTaskSaturationPolicy.desiredHeadcount(
                SwarmTaskType.ENGINEERING, 1.0, 20));
    }

    @Test
    void lowerDemandRecruitsFewerWorkers() {
        int low = SwarmTaskSaturationPolicy.desiredHeadcount(
                SwarmTaskType.FLANK, 0.35, 12);
        int high = SwarmTaskSaturationPolicy.desiredHeadcount(
                SwarmTaskType.FLANK, 0.95, 12);

        assertTrue(high > low);
    }

    @Test
    void unsatisfiedTaskKeepsFullDemand() {
        assertEquals(0.9, SwarmTaskSaturationPolicy.adjustedDemand(
                SwarmTaskType.FLANK,
                0.9,
                10,
                1,
                SwarmTaskType.RESERVE
        ), 1e-9);
    }

    @Test
    void saturatedTaskStronglyDiscouragesNewRecruit() {
        double adjusted = SwarmTaskSaturationPolicy.adjustedDemand(
                SwarmTaskType.ENGINEERING,
                1.0,
                10,
                2,
                SwarmTaskType.BREACH
        );

        assertTrue(adjusted < 0.25);
    }

    @Test
    void additionalOversupplyFurtherSuppressesRecruitment() {
        double justFull = SwarmTaskSaturationPolicy.adjustedDemand(
                SwarmTaskType.FLANK,
                0.9,
                10,
                3,
                SwarmTaskType.RESERVE
        );
        double oversupplied = SwarmTaskSaturationPolicy.adjustedDemand(
                SwarmTaskType.FLANK,
                0.9,
                10,
                5,
                SwarmTaskType.RESERVE
        );

        assertTrue(oversupplied < justFull);
    }

    @Test
    void incumbentGetsSofterPenaltyThanNewRecruit() {
        double incumbent = SwarmTaskSaturationPolicy.adjustedDemand(
                SwarmTaskType.ENGINEERING,
                1.0,
                10,
                2,
                SwarmTaskType.ENGINEERING
        );
        double recruit = SwarmTaskSaturationPolicy.adjustedDemand(
                SwarmTaskType.ENGINEERING,
                1.0,
                10,
                2,
                SwarmTaskType.RESERVE
        );

        assertTrue(incumbent > recruit);
        assertTrue(incumbent > 0.5);
    }

    @Test
    void reserveDemandIsNeverSaturationSuppressed() {
        assertEquals(0.3, SwarmTaskSaturationPolicy.adjustedDemand(
                SwarmTaskType.RESERVE,
                0.3,
                20,
                19,
                SwarmTaskType.RESERVE
        ), 1e-9);
    }
}
