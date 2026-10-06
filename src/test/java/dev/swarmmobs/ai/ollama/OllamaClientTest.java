package dev.swarmmobs.ai.ollama;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {

    @Test
    void acceptsLoopbackHostsOnly() {
        assertTrue(OllamaClient.isLoopbackHost("localhost"));
        assertTrue(OllamaClient.isLoopbackHost("127.0.0.1"));
        assertTrue(OllamaClient.isLoopbackHost("::1"));

        assertFalse(OllamaClient.isLoopbackHost("example.com"));
        assertFalse(OllamaClient.isLoopbackHost("192.168.1.20"));
        assertFalse(OllamaClient.isLoopbackHost(null));
    }

    @Test
    void rejectsRemoteBaseUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OllamaClient.localApiUri("https://example.com", "/api/chat")
        );
    }

    @Test
    void buildsExpectedLocalApiPath() {
        var uri = OllamaClient.localApiUri(
                "http://127.0.0.1:11434/",
                "/api/chat"
        );

        assertEquals("http://127.0.0.1:11434/api/chat", uri.toString());
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OllamaClient.localApiUri("file://localhost/tmp", "/api/chat")
        );
    }
}
