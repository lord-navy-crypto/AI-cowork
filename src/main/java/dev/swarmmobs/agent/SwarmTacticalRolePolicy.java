package dev.swarmmobs.agent;

public final class SwarmTacticalRolePolicy {

    public static SwarmRole roleFor(
            SwarmAgentArchetype archetype,
            int stableSlot
    ) {
        if (archetype == SwarmAgentArchetype.RANGED_SUPPORT) {
            return SwarmRole.RANGED_SUPPORT;
        }

        return switch (Math.floorMod(stableSlot, 4)) {
            case 0 -> SwarmRole.CHASER;
            case 1 -> SwarmRole.FLANK_LEFT;
            case 2 -> SwarmRole.FLANK_RIGHT;
            default -> SwarmRole.REAR_PRESSURE;
        };
    }

    private SwarmTacticalRolePolicy() {}
}
