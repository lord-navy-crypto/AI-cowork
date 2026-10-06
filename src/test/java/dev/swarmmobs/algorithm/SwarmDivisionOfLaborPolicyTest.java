package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmSpecialization;
import dev.swarmmobs.agent.SwarmTaskType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmDivisionOfLaborPolicyTest {

    @Test
    void blockedRouteRaisesEngineeringAndMaterialDemand() {
        var signals = new SwarmTaskDemandPolicy.Signals(
                false, true, false, false, true, true, 1.0
        );

        assertEquals(1.0, SwarmTaskDemandPolicy.demand(SwarmTaskType.ENGINEERING, signals), 1e-9);
        assertTrue(SwarmTaskDemandPolicy.demand(SwarmTaskType.MATERIAL, signals) > 0.8);
    }

    @Test
    void staleTargetMakesSearchDominant() {
        var signals = new SwarmTaskDemandPolicy.Signals(
                true, false, false, false, true, true, 0.2
        );

        assertEquals(1.0, SwarmTaskDemandPolicy.demand(SwarmTaskType.SEARCH, signals), 1e-9);
        assertTrue(
                SwarmTaskDemandPolicy.demand(SwarmTaskType.SEARCH, signals)
                        > SwarmTaskDemandPolicy.demand(SwarmTaskType.BREACH, signals)
        );
    }

    @Test
    void specialistCapabilityBeatsPoorMatch() {
        UUID spider = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID skeleton = UUID.fromString("00000000-0000-0000-0000-000000000002");

        UUID winner = SwarmTaskBidPolicy.winner(
                SwarmTaskType.FLANK,
                0.9,
                List.of(
                        new SwarmTaskBidPolicy.Candidate(
                                skeleton, SwarmAgentArchetype.RANGED_SUPPORT,
                                SwarmTaskType.RANGED_SUPPORT, 0.0, 0.0, false
                        ),
                        new SwarmTaskBidPolicy.Candidate(
                                spider, SwarmAgentArchetype.FLANKER,
                                SwarmTaskType.FLANK, 0.0, 0.4, false
                        )
                )
        );

        assertEquals(spider, winner);
    }

    @Test
    void experienceCanBreakCloseCapabilityTieWithoutIgnoringSwitchCost() {
        UUID experienced = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID novice = UUID.fromString("00000000-0000-0000-0000-000000000020");

        UUID winner = SwarmTaskBidPolicy.winner(
                SwarmTaskType.ENGINEERING,
                1.0,
                List.of(
                        new SwarmTaskBidPolicy.Candidate(
                                experienced, SwarmAgentArchetype.ASSAULT,
                                SwarmTaskType.ENGINEERING, 1.0, 0.3, false
                        ),
                        new SwarmTaskBidPolicy.Candidate(
                                novice, SwarmAgentArchetype.ASSAULT,
                                SwarmTaskType.BREACH, 0.0, 0.0, false
                        )
                )
        );

        assertEquals(experienced, winner);
    }

    @Test
    void responseThresholdIsDeterministicAndHighDemandRecruits() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
        double thresholdA = SwarmTaskBidPolicy.responseThreshold(
                id,
                SwarmTaskType.SEARCH,
                SwarmTaskBidPolicy.capability(SwarmAgentArchetype.FLANKER, SwarmTaskType.SEARCH)
        );
        double thresholdB = SwarmTaskBidPolicy.responseThreshold(
                id,
                SwarmTaskType.SEARCH,
                SwarmTaskBidPolicy.capability(SwarmAgentArchetype.FLANKER, SwarmTaskType.SEARCH)
        );

        assertEquals(thresholdA, thresholdB, 1e-12);

        var low = SwarmTaskBidPolicy.bid(
                new SwarmTaskBidPolicy.Candidate(
                        id,
                        SwarmAgentArchetype.FLANKER,
                        SwarmTaskType.RESERVE,
                        0.0,
                        0.0,
                        false
                ),
                SwarmTaskType.SEARCH,
                0.05
        );
        var high = SwarmTaskBidPolicy.bid(
                new SwarmTaskBidPolicy.Candidate(
                        id,
                        SwarmAgentArchetype.FLANKER,
                        SwarmTaskType.RESERVE,
                        0.0,
                        0.0,
                        false
                ),
                SwarmTaskType.SEARCH,
                1.0
        );

        assertFalse(Double.isFinite(low.utility()));
        assertTrue(Double.isFinite(high.utility()));
    }

    @Test
    void genericAllocatorKeepsDeterministicHighestUtilityWinner() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID b = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

        assertEquals(
                b,
                SwarmTaskAllocator.winner(List.of(
                        new SwarmTaskAllocator.Offer(a, 0.5, true),
                        new SwarmTaskAllocator.Offer(b, 0.8, true)
                ))
        );
    }

    @Test
    void specializationMapsSpeciesAndStableSlotIntoSubroles() {
        assertEquals(
                SwarmSpecialization.CROSSFIRE_LEFT,
                SwarmSpecialization.forAssignment(
                        SwarmAgentArchetype.RANGED_SUPPORT,
                        SwarmTaskType.RANGED_SUPPORT,
                        0
                )
        );
        assertEquals(
                SwarmSpecialization.INTERCEPTOR,
                SwarmSpecialization.forAssignment(
                        SwarmAgentArchetype.FLANKER,
                        SwarmTaskType.FLANK,
                        2
                )
        );
        assertEquals(
                SwarmSpecialization.ENGINEER,
                SwarmSpecialization.forAssignment(
                        SwarmAgentArchetype.ASSAULT,
                        SwarmTaskType.ENGINEERING,
                        0
                )
        );
    }
}
