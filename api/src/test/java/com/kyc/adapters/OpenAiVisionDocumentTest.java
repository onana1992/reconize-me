package com.kyc.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.ProviderUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiVisionDocumentTest {

    private static final String API_KEY = "sk-test-local";
    private static final byte[] IMAGE = new byte[] {1, 2, 3, 4};

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private int status = 200;
    private String responseBody =
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"detection\\\":\\\"DOCUMENT_PRESENT\\\"}\"}}]}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void returnsOnlyTheModelJson() throws Exception {
        String raw = client(Duration.ofSeconds(5)).complete(IMAGE, "image/png", "catalog prompt");

        assertEquals("{\"detection\":\"DOCUMENT_PRESENT\"}", raw);
        assertEquals("Bearer " + API_KEY, authorization.get());
        JsonNode body = mapper.readTree(requestBody.get());
        assertEquals("gpt-4.1-mini", body.get("model").asText());
        assertEquals("json_object", body.get("response_format").get("type").asText());
        String system = body.get("messages").get(0).get("content").asText();
        assertTrue(system.contains("\"detection\":\"DOCUMENT_PRESENT\""));
        assertTrue(system.contains("\"fields\":["));
        assertEquals("catalog prompt", body.get("messages").get(1).get("content").get(0).get("text").asText());
        assertTrue(body.get("messages").get(1).get("content").get(1).get("image_url").get("url").asText()
                .startsWith("data:image/png;base64,"));
        assertFalse(requestBody.get().contains(API_KEY));
        assertEquals(1, calls.get());
    }

    @Test
    void httpErrorDoesNotExposeTheBody() {
        status = 429;
        responseBody = "{\"error\":\"slow down\",\"echo\":\"catalog prompt\"}";

        ProviderUnavailableException error = assertThrows(
                ProviderUnavailableException.class,
                () -> client(Duration.ofSeconds(5)).complete(IMAGE, "image/jpeg", "catalog prompt"));

        assertEquals("openai_http_429", error.getMessage());
        assertFalse(error.getMessage().contains("catalog prompt"));
    }

    @Test
    void missingContentIsUnavailable() {
        responseBody = "{\"choices\":[]}";

        assertThrows(
                ProviderUnavailableException.class,
                () -> client(Duration.ofSeconds(5)).complete(IMAGE, "image/jpeg", "catalog prompt"));
    }

    @Test
    void timeoutIsUnavailable() {
        server.removeContext("/v1/chat/completions");
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        assertThrows(
                ProviderUnavailableException.class,
                () -> client(Duration.ofMillis(200)).complete(IMAGE, "image/jpeg", "catalog prompt"));
    }

    @Test
    void blankInputDoesNotCallTheNetwork() {
        assertThrows(
                ProviderUnavailableException.class,
                () -> client(Duration.ofSeconds(5)).complete(new byte[0], "image/jpeg", "catalog prompt"));
        assertEquals(0, calls.get());
    }

    @Test
    void missingKeyFailsBeforeAnyCall() {
        assertThrows(
                IllegalStateException.class,
                () -> new OpenAiVisionDocument("  ", "gpt-4.1-mini", mapper));
    }

    private OpenAiVisionDocument client(Duration timeout) {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
        return new OpenAiVisionDocument(API_KEY, "gpt-4.1-mini", endpoint, timeout, mapper);
    }
}
