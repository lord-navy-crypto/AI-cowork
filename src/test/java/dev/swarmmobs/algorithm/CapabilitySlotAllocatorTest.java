package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapabilitySlotAllocatorTest {

    private static final UUID ASSAULT_A = new UUID(0L, 10L);
    private static final UUID ASSAULT_B = new UUID(0L, 20L);
    private static final UUID SUPPORT_A = new UUID(0L, 5L);
    private static final UUID SUPPORT_B = new UUID(0L, 15L);
    private static final UUID FLANKER = new UUID(0L, 1L);

    @Test
    void foreignArchetypesDoNotReshuffleAssaultSlot() {
        int assaultOnly = CapabilitySlotAllocator.allocate(
                ASSAULT_B,
                SwarmAgentArchetype.ASSAULT,
                List.of(
                        member(ASSAULT_A, SwarmAgentArchetype.ASSAULT)
                ),
                8
        );

        int mixedTeam = CapabilitySlotAllocator.allocate(
                ASSAULT_B,
                SwarmAgentArchetype.ASSAULT,
                List.of(
                        member(ASSAULT_A, SwarmAgentArchetype.ASSAULT),
                        member(SUPPORT_A, SwarmAgentArchetype.RANGED_SUPPORT),
                        member(SUPPORT_B, SwarmAgentArchetype.RANGED_SUPPORT),
                        member(FLANKER, SwarmAgentArchetype.FLANKER)
                ),
                8
        );

        assertEquals(assaultOnly, mixedTeam);
    }

    @Test
    void sameCapabilityPeersStillReceiveDistinctLocalSlots() {
        int first = CapabilitySlotAllocator.allocate(
                SUPPORT_A,
                SwarmAgentArchetype.RANGED_SUPPORT,
                List.of(member(SUPPORT_B, SwarmAgentArchetype.RANGED_SUPPORT)),
                8
        );

        int second = CapabilitySlotAllocator.allocate(
                SUPPORT_B,
                SwarmAgentArchetype.RANGED_SUPPORT,
                List.of(member(SUPPORT_A, SwarmAgentArchetype.RANGED_SUPPORT)),
                8
        );

        assertNotEquals(first, second);
    }

    @Test
    void aSingleFlankerKeepsSlotZeroRegardlessOfOtherCapabilities() {
        int slot = CapabilitySlotAllocator.allocate(
                FLANKER,
                SwarmAgentArchetype.FLANKER,
                List.of(
                        member(ASSAULT_A, SwarmAgentArchetype.ASSAULT),
                        member(SUPPORT_A, SwarmAgentArchetype.RANGED_SUPPORT)
                ),
                8
        );

        assertEquals(0, slot);
    }

    private static CapabilitySlotAllocator.Member member(
            UUID id,
            SwarmAgentArchetype archetype
    ) {
        return new CapabilitySlotAllocator.Member(id, archetype);
    }
}
