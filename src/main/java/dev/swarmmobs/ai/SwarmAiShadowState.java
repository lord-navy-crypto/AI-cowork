package dev.swarmmobs.ai;

public final class SwarmAiShadowState {
    public enum Status {
        IDLE,
        REQUESTING,
        SUCCESS,
        FALLBACK,
        ERROR
    }

    private static Status status = Status.IDLE;
    private static long requestSequence;
    private static long completedSequence;
    private static long lastRequestGameTick = Long.MIN_VALUE;
    private static long lastLatencyMs;
    private static long successCount;
    private static long fallbackCount;
    private static long errorCount;
    private static String lastError = "";
    private static SwarmStrategyDecision lastDecision =
            SwarmStrategyDecision.baseline("none", "No shadow decision has been requested yet.");

    public static synchronized long begin(long gameTick) {
        requestSequence++;
        lastRequestGameTick = gameTick;
        status = Status.REQUESTING;
        lastError = "";
        return requestSequence;
    }

    public static synchronized void complete(
            long sequence,
            SwarmStrategyDecision decision,
            long latencyMs
    ) {
        if (sequence < completedSequence) {
            return;
        }
        completedSequence = sequence;
        lastLatencyMs = Math.max(0L, latencyMs);
        lastDecision = decision == null
                ? SwarmStrategyDecision.baseline("unknown", "Empty AI decision.")
                : decision.sanitized();

        if (lastDecision.providerId().startsWith("deterministic")) {
            status = Status.FALLBACK;
            fallbackCount++;
        } else {
            status = Status.SUCCESS;
            successCount++;
        }
    }

    public static synchronized void fail(long sequence, Throwable error, long latencyMs) {
        if (sequence < completedSequence) {
            return;
        }
        completedSequence = sequence;
        lastLatencyMs = Math.max(0L, latencyMs);
        status = Status.ERROR;
        errorCount++;
        lastError = conciseError(error);
        lastDecision = SwarmStrategyDecision.baseline(
                "deterministic-fallback",
                "AI request failed; deterministic gameplay remained unchanged."
        );
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(
                status,
                requestSequence,
                completedSequence,
                lastRequestGameTick,
                lastLatencyMs,
                successCount,
                fallbackCount,
                errorCount,
                lastDecision,
                lastError
        );
    }

    private static String conciseError(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public record Snapshot(
            Status status,
            long requestSequence,
            long completedSequence,
            long lastRequestGameTick,
            long lastLatencyMs,
            long successCount,
            long fallbackCount,
            long errorCount,
            SwarmStrategyDecision lastDecision,
            String lastError
    ) {}

    private SwarmAiShadowState() {}
}
