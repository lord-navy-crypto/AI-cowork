package dev.swarmmobs.ai;

public record ProviderStatus(
        boolean available,
        String providerId,
        String endpoint,
        String model,
        String message
) {}
