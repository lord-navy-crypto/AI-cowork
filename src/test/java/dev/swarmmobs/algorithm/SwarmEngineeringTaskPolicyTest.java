package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmEngineeringTask;
import dev.swarmmobs.agent.SwarmRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmEngineeringTaskPolicyTest {

    @Test
    void bridgeRequiresCarriedMaterial() {
        UUID requester = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID helper = UUID.fromString("00000000-0000-0000-0000-000000000002");

        UUID winner = SwarmEngineeringTaskPolicy.chooseClaimant(
                SwarmEngineeringTask.Type.BRIDGE,
                List.of(
                        new SwarmEngineeringTaskPolicy.Candidate(
                                requester, 1.0, SwarmRole.REAR_PRESSURE, 0, true, false, true
                        ),
                        new SwarmEngineeringTaskPolicy.Candidate(
                                helper, 9.0, SwarmRole.CHASER, 1, false, false, true
                        )
                )
        );

        assertEquals(helper, winner);
    }

    @Test
    void prefersNearbyRearPressureOverFrontLineChaserWhenCostIsSimilar() {
        UUID chaser = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID rear = UUID.fromString("00000000-0000-0000-0000-000000000020");

        UUID winner = SwarmEngineeringTaskPolicy.chooseClaimant(
                SwarmEngineeringTask.Type.BREAK,
                List.of(
                        new SwarmEngineeringTaskPolicy.Candidate(
                                chaser, 4.0, SwarmRole.CHASER, 0, false, false, true
                        ),
                        new SwarmEngineeringTaskPolicy.Candidate(
                                rear, 5.0, SwarmRole.REAR_PRESSURE, 0, false, false, true
                        )
                )
        );

        assertEquals(rear, winner);
    }

    @Test
    void meleeBusyCandidateIsNeverClaimed() {
        UUID busy = UUID.fromString("00000000-0000-0000-0000-000000000100");
        UUID free = UUID.fromString("00000000-0000-0000-0000-000000000200");

        UUID winner = SwarmEngineeringTaskPolicy.chooseClaimant(
                SwarmEngineeringTask.Type.BREAK,
                List.of(
                        new SwarmEngineeringTaskPolicy.Candidate(
                                busy, 1.0, SwarmRole.REAR_PRESSURE, 0, false, true, true
                        ),
                        new SwarmEngineeringTaskPolicy.Candidate(
                                free, 16.0, SwarmRole.CHASER, 0, false, false, true
                        )
                )
        );

        assertEquals(free, winner);
    }

    @Test
    void unreachableCloserCandidateLosesToReachableHelper() {
        UUID blocked = UUID.fromString("00000000-0000-0000-0000-000000000300");
        UUID reachable = UUID.fromString("00000000-0000-0000-0000-000000000400");

        UUID winner = SwarmEngineeringTaskPolicy.chooseClaimant(
                SwarmEngineeringTask.Type.BREAK,
                List.of(
                        new SwarmEngineeringTaskPolicy.Candidate(
                                blocked, 1.0, SwarmRole.REAR_PRESSURE, 0, false, false, false
                        ),
                        new SwarmEngineeringTaskPolicy.Candidate(
                                reachable, 25.0, SwarmRole.CHASER, 0, false, false, true
                        )
                )
        );

        assertEquals(reachable, winner);
    }

    @Test
    void tieBreakIsDeterministicByUuid() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID b = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

        var candidates = List.of(
                new SwarmEngineeringTaskPolicy.Candidate(
                        b, 4.0, SwarmRole.REAR_PRESSURE, 0, false, false, true
                ),
                new SwarmEngineeringTaskPolicy.Candidate(
                        a, 4.0, SwarmRole.REAR_PRESSURE, 0, false, false, true
                )
        );

        assertEquals(
                a,
                SwarmEngineeringTaskPolicy.chooseClaimant(
                        SwarmEngineeringTask.Type.BREAK,
                        candidates
                )
        );
    }
}
