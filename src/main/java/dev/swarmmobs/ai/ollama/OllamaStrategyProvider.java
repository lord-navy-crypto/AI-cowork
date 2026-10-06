package dev.swarmmobs.ai.ollama;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.swarmmobs.ai.ProviderStatus;
import dev.swarmmobs.ai.SwarmStrategyDecision;
import dev.swarmmobs.ai.SwarmStrategyProvider;
import dev.swarmmobs.ai.SwarmStrategyRequest;
import dev.swarmmobs.config.SwarmConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class OllamaStrategyProvider implements SwarmStrategyProvider {
    public static final OllamaStrategyProvider INSTANCE = new OllamaStrategyProvider();

    private static final Gson GSON = new Gson();
    private static final JsonObject DECISION_SCHEMA = buildDecisionSchema();

    private static final String SYSTEM_PROMPT = """
            You are a high-level strategy advisor for a Minecraft swarm simulation.
            You never control per-tick movement, attacks, exact coordinates, path nodes, or individual mobs.
            Choose one coarse swarm strategy and bounded multipliers for the existing deterministic controller.
            Prefer BASELINE when telemetry does not justify a change.
            Reason only from the supplied aggregate telemetry.
            Keep rationale short and technical.
            """;

    private final OllamaClient client = new OllamaClient();

    private OllamaStrategyProvider() {}

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public CompletableFuture<ProviderStatus> status() {
        String endpoint = SwarmConfig.OLLAMA_BASE_URL.get();
        String model = SwarmConfig.OLLAMA_MODEL.get();

        return listModels()
                .thenApply(models -> {
                    boolean selectedPresent = model == null || model.isBlank() || models.contains(model);
                    String message = models.isEmpty()
                            ? "Ollama responded, but no local models were reported."
                            : "Ollama online; " + models.size() + " local model(s) available"
                                    + (selectedPresent ? "." : "; selected model is not installed.");
                    return new ProviderStatus(true, id(), endpoint, model, message);
                })
                .exceptionally(error -> new ProviderStatus(
                        false,
                        id(),
                        endpoint,
                        model,
                        conciseError(error)
                ));
    }

    @Override
    public CompletableFuture<List<String>> listModels() {
        return client.listModels(
                SwarmConfig.OLLAMA_BASE_URL.get(),
                SwarmConfig.OLLAMA_TIMEOUT_MS.get()
        );
    }

    @Override
    public CompletableFuture<SwarmStrategyDecision> decide(SwarmStrategyRequest request) {
        String model = SwarmConfig.OLLAMA_MODEL.get();
        String userPrompt = """
                Select one high-level shadow recommendation for this aggregate swarm state.

                Snapshot JSON:
                %s

                This is SHADOW MODE. The recommendation will be recorded and displayed only.
                Return a JSON object matching the provided schema.
                """.formatted(GSON.toJson(request));

        return client.chatStructured(
                        SwarmConfig.OLLAMA_BASE_URL.get(),
                        model,
                        SYSTEM_PROMPT,
                        userPrompt,
                        DECISION_SCHEMA,
                        SwarmConfig.OLLAMA_TIMEOUT_MS.get(),
                        SwarmConfig.OLLAMA_KEEP_ALIVE.get()
                )
                .thenApply(this::parseDecision);
    }

    private SwarmStrategyDecision parseDecision(String content) {
        DecisionPayload payload = GSON.fromJson(content, DecisionPayload.class);
        if (payload == null) {
            throw new IllegalStateException("Ollama returned an empty structured decision.");
        }

        SwarmStrategyDecision.Mode mode;
        try {
            mode = SwarmStrategyDecision.Mode.valueOf(
                    payload.mode == null ? "BASELINE" : payload.mode
            );
        } catch (IllegalArgumentException exception) {
            mode = SwarmStrategyDecision.Mode.BASELINE;
        }

        return new SwarmStrategyDecision(
                mode,
                payload.formationRadiusMultiplier,
                payload.separationMultiplier,
                payload.cohesionMultiplier,
                payload.searchRadiusMultiplier,
                payload.rationale,
                id() + ":" + SwarmConfig.OLLAMA_MODEL.get()
        ).sanitized();
    }

    private static JsonObject buildDecisionSchema() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        root.addProperty("additionalProperties", false);

        JsonObject properties = new JsonObject();

        JsonObject mode = new JsonObject();
        mode.addProperty("type", "string");
        JsonArray modes = new JsonArray();
        for (var value : SwarmStrategyDecision.Mode.values()) {
            modes.add(value.name());
        }
        mode.add("enum", modes);
        properties.add("mode", mode);

        properties.add("formationRadiusMultiplier", boundedNumber(0.70, 1.50));
        properties.add("separationMultiplier", boundedNumber(0.70, 1.50));
        properties.add("cohesionMultiplier", boundedNumber(0.70, 1.50));
        properties.add("searchRadiusMultiplier", boundedNumber(0.80, 1.40));

        JsonObject rationale = new JsonObject();
        rationale.addProperty("type", "string");
        rationale.addProperty("maxLength", 240);
        properties.add("rationale", rationale);

        root.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("mode");
        required.add("formationRadiusMultiplier");
        required.add("separationMultiplier");
        required.add("cohesionMultiplier");
        required.add("searchRadiusMultiplier");
        required.add("rationale");
        root.add("required", required);

        return root;
    }

    private static JsonObject boundedNumber(double min, double max) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "number");
        value.addProperty("minimum", min);
        value.addProperty("maximum", max);
        return value;
    }

    private static String conciseError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static final class DecisionPayload {
        String mode;
        double formationRadiusMultiplier = 1.0;
        double separationMultiplier = 1.0;
        double cohesionMultiplier = 1.0;
        double searchRadiusMultiplier = 1.0;
        String rationale = "";
    }
}
