package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Locally observed, decentralized Minecraft Spider flank coverage.
 * Stable UUID order yields both sides without a squad commander, global
 * searches, teleportation, extra attacks or a battle turn system.
 */
public final class SwarmSpiderFlankBalancePolicy {
    public static SwarmRole choose(UUID self, SwarmRole existing,
            List<UUID> sameTargetSpiders, boolean coordinating) {
        SwarmRole normal = existing == null ? SwarmRole.FLANK_LEFT : existing;
        if (!coordinating || self == null || sameTargetSpiders == null) {
            return normal;
        }
        List<UUID> known = new ArrayList<>();
        known.add(self);
        for (UUID peer : sameTargetSpiders) {
            if (peer != null && !known.contains(peer)) known.add(peer);
        }
        if (known.size() < 2) return normal;
        known.sort(UUID::compareTo);
        int index = known.indexOf(self);
        return index % 2 == 0 ? SwarmRole.FLANK_LEFT : SwarmRole.FLANK_RIGHT;
    }

    private SwarmSpiderFlankBalancePolicy() {}
}
