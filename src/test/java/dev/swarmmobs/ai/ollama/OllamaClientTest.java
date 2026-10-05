package dev.swarmmobs.ai.ollama;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {

    @Test
    void defaultLocalEndpointBuildsNativeApiPath() {
        URI uri = OllamaClient.localApiUri("http://127.0.0.1:11434", "/api/chat");
        assertEquals("http://127.0.0.1:11434/api/chat", uri.toString());
    }

    @Test
    void localhostIsAccepted() {
        URI uri = OllamaClient.localApiUri("http://localhost:11434/", "/api/tags");
        assertEquals("http://localhost:11434/api/tags", uri.toString());
    }

    @Test
    void ipv6LoopbackIsAccepted() {
        URI uri = OllamaClient.localApiUri("http://[::1]:11434", "/api/tags");
        assertEquals("http://[::1]:11434/api/tags", uri.toString());
    }

    @Test
    void remoteHostIsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaClient.localApiUri("https://example.com", "/api/chat")
        );
        assertTrue(error.getMessage().contains("local Ollama"));
    }

    @Test
    void unsupportedSchemeIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OllamaClient.localApiUri("ftp://127.0.0.1:11434", "/api/chat")
        );
    }
}
