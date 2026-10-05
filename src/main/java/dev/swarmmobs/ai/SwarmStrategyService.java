package dev.swarmmobs.ai;

import dev.swarmmobs.ai.ollama.OllamaStrategyProvider;
import dev.swarmmobs.config.SwarmConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Single entry point for future gameplay integration.
 *
 * v0.2 exposes the provider interface but does not apply AI decisions to mobs yet.
 */
public final class SwarmStrategyService {
    private static final OllamaStrategyProvider OLLAMA = OllamaStrategyProvider.INSTANCE;

    public static OllamaStrategyProvider ollama() {
        return OLLAMA;
    }

    public static CompletableFuture<SwarmStrategyDecision> decideWithFallback(
            SwarmStrategyRequest request
    ) {
        if (!SwarmConfig.EXTERNAL_AI_ENABLED.get()) {
            return DeterministicStrategyProvider.INSTANCE.decide(request);
        }

        return OLLAMA.decide(request)
                .exceptionally(error -> SwarmStrategyDecision.baseline(
                        "deterministic-fallback",
                        "Ollama unavailable or returned an invalid decision; deterministic baseline preserved."
                ));
    }

    private SwarmStrategyService() {}
}
