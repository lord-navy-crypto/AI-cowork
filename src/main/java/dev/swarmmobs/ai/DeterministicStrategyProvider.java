package dev.swarmmobs.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DeterministicStrategyProvider implements SwarmStrategyProvider {
    public static final DeterministicStrategyProvider INSTANCE = new DeterministicStrategyProvider();

    private DeterministicStrategyProvider() {}

    @Override
    public String id() {
        return "deterministic";
    }

    @Override
    public CompletableFuture<ProviderStatus> status() {
        return CompletableFuture.completedFuture(
                new ProviderStatus(true, id(), "internal", "none", "Deterministic fallback is available.")
        );
    }

    @Override
    public CompletableFuture<List<String>> listModels() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<SwarmStrategyDecision> decide(SwarmStrategyRequest request) {
        return CompletableFuture.completedFuture(
                SwarmStrategyDecision.baseline(
                        id(),
                        "Deterministic baseline preserved; no external AI strategy was applied."
                )
        );
    }
}
