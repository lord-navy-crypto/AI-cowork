package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.agent.SwarmTaskType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Decentralized Minecraft NPC replacement of missing flank roles.
 *
 * Each mob uses only the already sampled same-target teammates. It does not
 * create a central squad leader, issue real-world tactics, change the target,
 * bypass vanilla attacks or require a new entity scan.
 */
public final class SwarmLocalRoleCoveragePolicy {
    public record Member(UUID id, SwarmAgentArchetype archetype,
                         SwarmRole role, SwarmTaskType task) {}

    public record Coverage(SwarmRole role, boolean fillingMissingFlank,
                           boolean frontAnchor, int availableAssaults) {}

    public static Coverage choose(UUID selfId, SwarmAgentArchetype selfType,
            SwarmTaskType currentTask, SwarmRole baseline, List<Member> nearby,
            boolean coordinating) {
        SwarmRole original = baseline == null ? SwarmRole.CHASER : baseline;
        Coverage ordinary = new Coverage(original, false, false, 0);
        if (!coordinating || selfId == null || selfType != SwarmAgentArchetype.ASSAULT
                || isCommittedWorker(currentTask) || nearby == null) {
            return ordinary;
        }

        // The currently observed Spider role, rather than its expected rank,
        // determines which flank is actually covered *right now*.
        boolean leftCovered = false;
        boolean rightCovered = false;
        List<UUID> assaultIds = new ArrayList<>();
        assaultIds.add(selfId);
        for (Member member : nearby) {
            if (member == null || member.id() == null
                    || member.id().equals(selfId)) continue;
            if (member.archetype() == SwarmAgentArchetype.FLANKER) {
                if (member.role() == SwarmRole.FLANK_LEFT) leftCovered = true;
                if (member.role() == SwarmRole.FLANK_RIGHT) rightCovered = true;
            } else if (member.archetype() == SwarmAgentArchetype.ASSAULT
                    && !isCommittedWorker(member.task())) {
                assaultIds.add(member.id());
            }
        }
        if (leftCovered && rightCovered) return ordinary;
        assaultIds = assaultIds.stream().distinct()
                .sorted(Comparator.naturalOrder()).toList();
        if (assaultIds.size() < 2) return ordinary;

        // Keep one eligible Zombie attacking from the front. Next eligible
        // Zombies fill currently vacant sides deterministically by UUID.
        int rank = assaultIds.indexOf(selfId);
        if (rank == 0) {
            return new Coverage(SwarmRole.CHASER, false,
                    original != SwarmRole.CHASER, assaultIds.size());
        }
        int firstFiller = 1;
        if (!leftCovered) {
            if (rank == firstFiller) {
                return new Coverage(SwarmRole.FLANK_LEFT,
                        true, false, assaultIds.size());
            }
            firstFiller++;
        }
        if (!rightCovered && rank == firstFiller) {
            return new Coverage(SwarmRole.FLANK_RIGHT,
                    true, false, assaultIds.size());
        }
        return new Coverage(original, false, false, assaultIds.size());
    }

    private static boolean isCommittedWorker(SwarmTaskType task) {
        return task == SwarmTaskType.ENGINEERING || task == SwarmTaskType.MATERIAL;
    }

    private SwarmLocalRoleCoveragePolicy() {}
}
