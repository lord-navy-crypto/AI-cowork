package dev.swarmmobs.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SwarmStrategyProvider {
    String id();

    CompletableFuture<ProviderStatus> status();

    CompletableFuture<List<String>> listModels();

    CompletableFuture<SwarmStrategyDecision> decide(SwarmStrategyRequest request);
}
