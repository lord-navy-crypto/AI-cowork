package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FormationSlotAllocatorTest {

    @Test
    void fullyConnectedEightAgentGroupGetsEightDistinctSlots() {
        List<UUID> members = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000008"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                UUID.fromString("00000000-0000-0000-0000-000000000005")
        );

        Set<Integer> slots = new HashSet<>();
        for (UUID self : members) {
            slots.add(FormationSlotAllocator.allocate(self, members, 8));
        }

        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), slots);
    }

    @Test
    void orderingOfNeighborInputDoesNotChangeAssignment() {
        UUID self = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<UUID> first = new ArrayList<>(List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                self,
                UUID.fromString("00000000-0000-0000-0000-000000000004")
        ));
        List<UUID> reversed = new ArrayList<>(first);
        java.util.Collections.reverse(reversed);

        assertEquals(
                FormationSlotAllocator.allocate(self, first, 8),
                FormationSlotAllocator.allocate(self, reversed, 8)
        );
    }

    @Test
    void groupsLargerThanSlotCountWrapPredictably() {
        List<UUID> members = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            members.add(new UUID(0L, i));
        }

        int slot9 = FormationSlotAllocator.allocate(members.get(8), members, 8);
        int slot1 = FormationSlotAllocator.allocate(members.get(0), members, 8);

        assertEquals(slot1, slot9);
    }
}
