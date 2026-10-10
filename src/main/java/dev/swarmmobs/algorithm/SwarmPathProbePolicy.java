package dev.swarmmobs.algorithm;

/**
 * Counts the candidates needing expensive PathNavigation evidence after cheap
 * terrain probing. Blocked candidates must not consume query quota because
 * callers never invoke createPath for them.
 */
public final class SwarmPathProbePolicy {
    public static int requiredQueries(boolean[] blocked, boolean evidenceEnabled) {
        if (!evidenceEnabled || blocked == null || blocked.length == 0) {
            return 0;
        }
        int queries = 0;
        for (boolean isBlocked : blocked) {
            if (!isBlocked) {
                queries++;
            }
        }
        return queries;
    }

    private SwarmPathProbePolicy() {}
}
