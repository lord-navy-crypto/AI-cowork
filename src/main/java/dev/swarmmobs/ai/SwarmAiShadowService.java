package dev.swarmmobs.ai;

import dev.swarmmobs.ai.ollama.OllamaStrategyProvider;
import dev.swarmmobs.config.SwarmConfig;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SwarmAiShadowService {
    private static final AtomicBoolean REQUEST_IN_FLIGHT = new AtomicBoolean(false);

    public static CompletableFuture<SwarmStrategyDecision> request(ServerLevel level) {
        return requestInternal(level, false);
    }

    public static CompletableFuture<SwarmStrategyDecision> requestActive(ServerLevel level) {
        if (!SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Active AI strategy is disabled.")
            );
        }
        return requestInternal(level, true);
    }

    private static CompletableFuture<SwarmStrategyDecision> requestInternal(
            ServerLevel level,
            boolean applyActive
    ) {
        if (!SwarmConfig.EXTERNAL_AI_ENABLED.get()) {
            var fallback = SwarmStrategyDecision.baseline(
                    "deterministic-fallback",
                    "Local AI is disabled; deterministic gameplay remains active."
            );
            SwarmAiShadowState.complete(
                    SwarmAiShadowState.begin(level.getGameTime()),
                    fallback,
                    0L
            );
            return CompletableFuture.completedFuture(fallback);
        }

        if (!REQUEST_IN_FLIGHT.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("A shadow AI request is already in flight.")
            );
        }

        SwarmStrategyRequest request = SwarmStrategyRequestFactory.from(level);
        long sequence = SwarmAiShadowState.begin(level.getGameTime());
        long startedNanos = System.nanoTime();

        CompletableFuture<SwarmStrategyDecision> future =
                OllamaStrategyProvider.INSTANCE.decide(request);

        future.whenComplete((decision, error) -> {
            long latencyMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
            level.getServer().execute(() -> {
                try {
                    if (error != null) {
                        SwarmAiShadowState.fail(sequence, error, latencyMs);
                    } else {
                        SwarmAiShadowState.complete(sequence, decision, latencyMs);
                        if (applyActive
                                && SwarmAiControlPolicy.mayApplyActiveStrategy(
                                        SwarmConfig.EXTERNAL_AI_ENABLED.get(),
                                        SwarmConfig.EXTERNAL_AI_ACTIVE_ENABLED.get()
                                )) {
                            SwarmAiActiveState.consider(
                                    decision,
                                    level.getGameTime(),
                                    SwarmConfig.EXTERNAL_AI_ACTIVE_TTL_TICKS.get(),
                                    SwarmConfig.EXTERNAL_AI_ACTIVE_MIN_HOLD_TICKS.get()
                            );
                        }
                    }
                } finally {
                    REQUEST_IN_FLIGHT.set(false);
                }
            });
        });

        return future;
    }

    public static boolean requestInFlight() {
        return REQUEST_IN_FLIGHT.get();
    }

    private SwarmAiShadowService() {}
}
