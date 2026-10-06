package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.agent.SwarmSpecialization;

public final class SwarmSpecializationRolePolicy {

    public static SwarmRole role(
            SwarmSpecialization specialization,
            SwarmRole baseline
    ) {
        SwarmRole fallback = baseline == null ? SwarmRole.CHASER : baseline;
        if (specialization == null) {
            return fallback;
        }

        return switch (specialization) {
            case VANGUARD, LEAD_BREACHER, INTERCEPTOR -> SwarmRole.CHASER;
            case ENGINEER, CARRIER, RESERVE, RESERVE_BREACHER -> SwarmRole.REAR_PRESSURE;
            case FLANKER_LEFT -> SwarmRole.FLANK_LEFT;
            case FLANKER_RIGHT -> SwarmRole.FLANK_RIGHT;
            case SCOUT -> fallback;
            case SUPPRESSOR, CROSSFIRE_LEFT, CROSSFIRE_RIGHT, OVERWATCH ->
                    SwarmRole.RANGED_SUPPORT;
        };
    }

    public static double formationRadiusMultiplier(
            SwarmSpecialization specialization
    ) {
        if (specialization == null) {
            return 1.0;
        }

        return switch (specialization) {
            case SUPPRESSOR -> 0.88;
            case CROSSFIRE_LEFT, CROSSFIRE_RIGHT -> 1.05;
            case OVERWATCH -> 1.28;
            case RESERVE, RESERVE_BREACHER -> 1.20;
            case ENGINEER, CARRIER -> 1.12;
            default -> 1.0;
        };
    }

    private SwarmSpecializationRolePolicy() {}
}
