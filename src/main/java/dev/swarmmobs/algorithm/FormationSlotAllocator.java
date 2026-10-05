package dev.swarmmobs.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Assigns stable local formation slots from the identities visible to one agent.
 *
 * If all members observe the same local group, each member receives a unique slot
 * until formationSlots is exhausted. This avoids random UUID-hash collisions in
 * small swarms while staying decentralized.
 */
public final class FormationSlotAllocator {

    public static int allocate(UUID self, List<UUID> localMembers, int formationSlots) {
        int slots = Math.max(1, formationSlots);

        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        unique.add(self);
        for (UUID member : localMembers) {
            if (member != null) {
                unique.add(member);
            }
        }

        List<UUID> ordered = new ArrayList<>(unique);
        ordered.sort(Comparator.naturalOrder());

        int index = ordered.indexOf(self);
        if (index < 0) {
            index = 0;
        }
        return Math.floorMod(index, slots);
    }

    private FormationSlotAllocator() {}
}
