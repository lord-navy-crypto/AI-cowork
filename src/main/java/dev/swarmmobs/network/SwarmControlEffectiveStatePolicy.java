package dev.swarmmobs.network;

/**
 * Pure presentation policy for the live Command Center snapshot.
 *
 * Cached agent state may intentionally survive a runtime toggle for continuity,
 * but the panel must describe what is currently effective rather than presenting
 * dormant cached state as active behavior.
 */
public final class SwarmControlEffectiveStatePolicy {

    public static boolean exposeDynamicAssignments(
            boolean masterEnabled,
            boolean divisionEnabled
    ) {
        return masterEnabled && divisionEnabled;
    }

    public static boolean exposeActiveAi(
            boolean masterEnabled,
            boolean externalAiEnabled,
            boolean activeAiEnabled,
            boolean activeSnapshot
    ) {
        return masterEnabled
                && externalAiEnabled
                && activeAiEnabled
                && activeSnapshot;
    }

    private SwarmControlEffectiveStatePolicy() {}
}
