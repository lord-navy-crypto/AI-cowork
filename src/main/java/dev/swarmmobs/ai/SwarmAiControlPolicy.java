package dev.swarmmobs.ai;

/**
 * Single source of truth for whether a bounded active AI strategy may affect
 * gameplay. Both the AI master switch and the explicit active-strategy switch
 * must be enabled.
 */
public final class SwarmAiControlPolicy {
    public static boolean mayApplyActiveStrategy(
            boolean aiMasterEnabled,
            boolean activeStrategyEnabled
    ) {
        return aiMasterEnabled && activeStrategyEnabled;
    }

    private SwarmAiControlPolicy() {}
}
