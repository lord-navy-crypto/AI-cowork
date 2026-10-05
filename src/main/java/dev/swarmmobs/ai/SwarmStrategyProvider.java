package dev.swarmmobs.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * High-level strategy provider boundary.
 *
 * Providers may use deterministic logic, a local model, or another future source,
 * but they must never perform per-tick Minecraft movement themselves.
 */
public interface SwarmStrategyProvider {
    String id();

    CompletableFuture<ProviderStatus> status();

    CompletableFuture<List<String>> listModels();

    CompletableFuture<SwarmStrategyDecision> decide(SwarmStrategyRequest request);
}
