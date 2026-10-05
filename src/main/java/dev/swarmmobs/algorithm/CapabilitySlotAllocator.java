package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;

import java.util.List;
import java.util.UUID;

/**
 * Capability-aware wrapper around the deterministic local slot allocator.
 *
 * Agents share perception, communication, cohesion, separation and alignment across
 * species, but tactical slots are allocated only among peers with the same capability
 * archetype. Adding a ranged-support agent therefore does not reshuffle assault roles.
 */
public final class CapabilitySlotAllocator {

    public record Member(UUID id, SwarmAgentArchetype archetype) {}

    public static int allocate(
            UUID self,
            SwarmAgentArchetype selfArchetype,
            List<Member> localMembers,
            int formationSlots
    ) {
        List<UUID> sameCapability = localMembers.stream()
                .filter(member -> member != null
                        && member.id() != null
                        && member.archetype() == selfArchetype)
                .map(Member::id)
                .toList();

        return FormationSlotAllocator.allocate(
                self,
                sameCapability,
                formationSlots
        );
    }

    private CapabilitySlotAllocator() {}
}
