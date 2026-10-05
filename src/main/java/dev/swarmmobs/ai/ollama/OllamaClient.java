package dev.swarmmobs.ai.ollama;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal dependency-free Ollama HTTP client.
 *
 * Only loopback endpoints are accepted. Network I/O always uses HttpClient.sendAsync
 * so Minecraft's server thread is never blocked waiting for local inference.
 */
public final class OllamaClient {
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;

    public OllamaClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public CompletableFuture<List<String>> listModels(String baseUrl, int timeoutMs) {
        URI uri = localApiUri(baseUrl, "/api/tags");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout(timeoutMs))
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(OllamaClient::requireSuccess)
                .thenApply(HttpResponse::body)
                .thenApply(OllamaClient::parseModelNames);
    }

    public CompletableFuture<String> chatStructured(
            String baseUrl,
            String model,
            String systemPrompt,
            String userPrompt,
            JsonObject schema,
            int timeoutMs,
            String keepAlive
    ) {
        if (model == null || model.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No Ollama model selected. Use /swarmmobs ai model <name>.")
            );
        }

        URI uri = localApiUri(baseUrl, "/api/chat");

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model.trim());
        payload.addProperty("stream", false);
        payload.add("format", schema);

        if (keepAlive != null && !keepAlive.isBlank()) {
            payload.addProperty("keep_alive", keepAlive.trim());
        }

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.0);
        payload.add("options", options);

        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        payload.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(OllamaClient::requireSuccess)
                .thenApply(HttpResponse::body)
                .thenApply(OllamaClient::extractAssistantContent);
    }

    static URI localApiUri(String baseUrl, String path) {
        String raw = baseUrl == null || baseUrl.isBlank()
                ? "http://127.0.0.1:11434"
                : baseUrl.trim();

        URI base = URI.create(raw);
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Ollama base URL must use http or https.");
        }

        String host = base.getHost();
        if (!isLoopbackHost(host)) {
            throw new IllegalArgumentException(
                    "Swarm Mobs v0.2 only permits local Ollama endpoints (localhost, 127.0.0.1, or ::1)."
            );
        }

        String normalized = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        return URI.create(normalized + path);
    }

    static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("[::1]");
    }

    private static Duration timeout(int timeoutMs) {
        return Duration.ofMillis(Math.max(250, Math.min(30_000, timeoutMs)));
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content == null ? "" : content);
        return message;
    }

    private static HttpResponse<String> requireSuccess(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                    "Ollama HTTP " + status + ": " + abbreviate(response.body(), 300)
            );
        }
        return response;
    }

    private static List<String> parseModelNames(String body) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        JsonArray models = root == null ? null : root.getAsJsonArray("models");
        if (models == null) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (JsonElement element : models) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject model = element.getAsJsonObject();
            JsonElement name = model.get("name");
            if (name == null || name.isJsonNull()) {
                name = model.get("model");
            }
            if (name != null && !name.isJsonNull() && !name.getAsString().isBlank()) {
                names.add(name.getAsString());
            }
        }
        return List.copyOf(names);
    }

    private static String extractAssistantContent(String body) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null || !root.has("message") || !root.get("message").isJsonObject()) {
            throw new IllegalStateException("Ollama response did not contain message.content.");
        }

        JsonObject message = root.getAsJsonObject("message");
        JsonElement content = message.get("content");
        if (content == null || content.isJsonNull()) {
            throw new IllegalStateException("Ollama response message.content was empty.");
        }

        return content.getAsString();
    }

    private static String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() <= maxLength
                ? singleLine
                : singleLine.substring(0, maxLength) + "...";
    }
}
