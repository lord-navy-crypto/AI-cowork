package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmEngineeringExecutionLeasePolicyTest {

    @Test
    void longBreakOutlivesShortAdvertisementTtl() {
        long deadline = SwarmEngineeringExecutionLeasePolicy.deadline(
                100L,
                140L,
                150
        );

        assertEquals(290L, deadline);
    }

    @Test
    void longerAdvertisementTtlRemainsAuthoritativeWhenAlreadySufficient() {
        long deadline = SwarmEngineeringExecutionLeasePolicy.deadline(
                100L,
                500L,
                15
        );

        assertEquals(500L, deadline);
    }

    @Test
    void minimumWorkBudgetIsStillBoundedAndPositive() {
        long deadline = SwarmEngineeringExecutionLeasePolicy.deadline(
                20L,
                30L,
                0
        );

        assertEquals(61L, deadline);
    }
}
