package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmLocalComposition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmTargetSquadPolicyTest {
    private static final UUID TARGET_A = new UUID(4L, 1L);
    private static final UUID TARGET_B = new UUID(4L, 2L);
    private record Peer(String name, UUID target, SwarmAgentArchetype archetype) {}

    @Test
    void mixedTargetsCreateIndependentTacticalSquadsWithoutReordering() {
        List<Peer> physicalNeighbors = List.of(
                new Peer("other-creeper", TARGET_B, SwarmAgentArchetype.BREACHER),
                new Peer("own-spider", TARGET_A, SwarmAgentArchetype.FLANKER),
                new Peer("unknown", null, SwarmAgentArchetype.ASSAULT),
                new Peer("own-creeper", TARGET_A, SwarmAgentArchetype.BREACHER)
        );

        var squad = SwarmTargetSquadPolicy.sameTarget(TARGET_A, physicalNeighbors, Peer::target);

        assertEquals(List.of("own-spider", "own-creeper"),
                squad.stream().map(Peer::name).toList());
        assertEquals(4, physicalNeighbors.size(),
                "Physical neighbor list must remain untouched for collision and relays");
    }

    @Test
    void unrelatedCreeperDoesNotChangeSkeletonFireSupportComposition() {
        var neighbors = List.of(
                new Peer("other-creeper", TARGET_B, SwarmAgentArchetype.BREACHER),
                new Peer("own-zombie", TARGET_A, SwarmAgentArchetype.ASSAULT)
        );
        var squad = SwarmTargetSquadPolicy.sameTarget(TARGET_A, neighbors, Peer::target);
        var composition = SwarmLocalComposition.fromArchetypes(
                SwarmAgentArchetype.RANGED_SUPPORT,
                squad.stream().map(Peer::archetype).toList()
        );
        assertEquals(0, composition.breacherCount());
        assertEquals(1, composition.assaultCount());
    }

    @Test
    void sameTargetCreeperDoesCreateBreacherSupportDemand() {
        var squad = SwarmTargetSquadPolicy.sameTarget(
                TARGET_A,
                List.of(new Peer("own-creeper", TARGET_A, SwarmAgentArchetype.BREACHER)),
                Peer::target
        );
        var composition = SwarmLocalComposition.fromArchetypes(
                SwarmAgentArchetype.RANGED_SUPPORT,
                squad.stream().map(Peer::archetype).toList()
        );
        assertTrue(composition.hasBreacher());
    }

    @Test
    void nullTargetNeverTreatsOtherUnknownPeersAsSquad() {
        var unknowns = List.of(
                new Peer("unknown", null, SwarmAgentArchetype.FLANKER),
                new Peer("known", TARGET_A, SwarmAgentArchetype.ASSAULT)
        );
        assertTrue(SwarmTargetSquadPolicy.sameTarget(null, unknowns, Peer::target).isEmpty());
        assertEquals(List.of("known"),
                SwarmTargetSquadPolicy.sameTarget(TARGET_A, unknowns, Peer::target)
                        .stream().map(Peer::name).toList());
    }

    @Test
    void emptyNeighborsHaveNoTacticalSquad() {
        assertTrue(SwarmTargetSquadPolicy.sameTarget(TARGET_A, List.<Peer>of(), Peer::target).isEmpty());
        assertTrue(SwarmTargetSquadPolicy.sameTarget(TARGET_A, null, Peer::target).isEmpty());
    }
}
