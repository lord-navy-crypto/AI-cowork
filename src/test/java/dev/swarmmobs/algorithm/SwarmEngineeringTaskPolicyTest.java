package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmEngineeringTask;
import dev.swarmmobs.agent.SwarmRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmEngineeringTaskPolicyTest {

    @Test
    void bridgeRequiresCarriedMaterial() {
        UUID requester = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID helper = UUID.fromString("00000000-0000-0000-0000-000000000002");

        UUID winner = SwarmEngineeringTaskPolicy.chooseClaimant(
                SwarmEngineeringTask.Type.BRIDGE,
                List.of(
                        new SwarmEngineeringTaskPolicy.Candidate(
                                requester, 1.0, SwarmRole.REAR_PRESSURE, 0, true, false
                        ),
                        new SwarmEngineeringTaskPolicy.Candidate(
                                helper, 9.0, SwarmRole.CHASER, 1, false, false
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
                                chaser, 4.0, SwarmRole.CHASER, 0, false, false
                        ),
                        new SwarmEngineeringTaskPolicy.Candidate(
                                rear, 5.0, SwarmRole.REAR_PRESSURE, 0, false, false
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
                                busy, 1.0, SwarmRole.REAR_PRESSURE, 0, false, true
                        ),
                        new SwarmEngineeringTaskPolicy.Candidate(
                                free, 16.0, SwarmRole.CHASER, 0, false, false
                        )
                )
        );

        assertEquals(free, winner);
    }

    @Test
    void tieBreakIsDeterministicByUuid() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID b = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

        var candidates = List.of(
                new SwarmEngineeringTaskPolicy.Candidate(
                        b, 4.0, SwarmRole.REAR_PRESSURE, 0, false, false
                ),
                new SwarmEngineeringTaskPolicy.Candidate(
                        a, 4.0, SwarmRole.REAR_PRESSURE, 0, false, false
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
