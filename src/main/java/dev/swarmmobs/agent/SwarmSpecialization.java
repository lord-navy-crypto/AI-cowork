package dev.swarmmobs.agent;

public enum SwarmSpecialization {
    VANGUARD(SwarmTaskType.BREACH),
    ENGINEER(SwarmTaskType.ENGINEERING),
    CARRIER(SwarmTaskType.MATERIAL),
    SCOUT(SwarmTaskType.SEARCH),
    FLANKER_LEFT(SwarmTaskType.FLANK),
    FLANKER_RIGHT(SwarmTaskType.FLANK),
    INTERCEPTOR(SwarmTaskType.FLANK),
    SUPPRESSOR(SwarmTaskType.RANGED_SUPPORT),
    CROSSFIRE_LEFT(SwarmTaskType.RANGED_SUPPORT),
    CROSSFIRE_RIGHT(SwarmTaskType.RANGED_SUPPORT),
    OVERWATCH(SwarmTaskType.RANGED_SUPPORT),
    LEAD_BREACHER(SwarmTaskType.BREACH),
    RESERVE_BREACHER(SwarmTaskType.RESERVE),
    RESERVE(SwarmTaskType.RESERVE);

    private final SwarmTaskType taskType;

    SwarmSpecialization(SwarmTaskType taskType) {
        this.taskType = taskType;
    }

    public SwarmTaskType taskType() {
        return taskType;
    }

    public static SwarmSpecialization forAssignment(
            SwarmAgentArchetype archetype,
            SwarmTaskType task,
            int stableSlot
    ) {
        if (archetype == null || task == null) {
            return RESERVE;
        }

        return switch (archetype) {
            case ASSAULT -> switch (task) {
                case ENGINEERING -> ENGINEER;
                case MATERIAL -> CARRIER;
                case SEARCH -> SCOUT;
                case FLANK -> Math.floorMod(stableSlot, 2) == 0 ? FLANKER_LEFT : FLANKER_RIGHT;
                case BREACH -> VANGUARD;
                case RANGED_SUPPORT, RESERVE -> RESERVE;
            };
            case RANGED_SUPPORT -> switch (task) {
                case SEARCH -> SCOUT;
                case RANGED_SUPPORT -> switch (Math.floorMod(stableSlot, 3)) {
                    case 0 -> CROSSFIRE_LEFT;
                    case 1 -> CROSSFIRE_RIGHT;
                    default -> OVERWATCH;
                };
                default -> SUPPRESSOR;
            };
            case FLANKER -> switch (task) {
                case SEARCH -> SCOUT;
                case FLANK -> Math.floorMod(stableSlot, 3) == 2
                        ? INTERCEPTOR
                        : (Math.floorMod(stableSlot, 2) == 0 ? FLANKER_LEFT : FLANKER_RIGHT);
                default -> RESERVE;
            };
            case BREACHER -> task == SwarmTaskType.BREACH ? LEAD_BREACHER : RESERVE_BREACHER;
        };
    }
}
