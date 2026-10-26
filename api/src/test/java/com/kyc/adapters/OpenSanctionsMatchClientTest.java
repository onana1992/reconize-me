package com.kyc.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.SanctionsMatchPort;
import com.kyc.services.aml.DeterministicAmlEngine;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OpenSanctionsMatchClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void scoreAboveThresholdIsKeptWhenProviderMatchIsFalse() throws Exception {
        try (Server server = new Server(1, 200, person(false))) {
            SanctionsMatchPort.MatchResult result = client(server).match(query());
            SanctionsMatchPort.MatchResult.Ok ok = assertInstanceOf(SanctionsMatchPort.MatchResult.Ok.class, result);
            assertEquals(1, ok.candidates().size());
            assertEquals(0, ok.candidates().get(0).score().compareTo(new BigDecimal("0.91")));
            assertEquals("NK-1", ok.candidates().get(0).id());
            assertTrue(ok.candidates().get(0).topics().contains("sanction"));
            assertEquals(1, server.calls.get());
        }
    }

    @Test
    void retryableStatusIsTriedOnce() throws Exception {
        try (Server server = new Server(1, 429, person(true))) {
            SanctionsMatchPort.MatchResult result = client(server).match(query());
            assertInstanceOf(SanctionsMatchPort.MatchResult.Ok.class, result);
            assertEquals(2, server.calls.get());
        }
    }

    @Test
    void unauthorizedIsNotRetried() throws Exception {
        try (Server server = new Server(2, 401, "{}")) {
            SanctionsMatchPort.MatchResult result = client(server).match(query());
            SanctionsMatchPort.MatchResult.Failed failed = assertInstanceOf(SanctionsMatchPort.MatchResult.Failed.class, result);
            assertEquals("provider_unavailable", failed.reason());
            assertEquals(1, server.calls.get());
        }
    }

    @Test
    void testEngineDoesNotDependOnTheHttpClient() {
        for (var constructor : DeterministicAmlEngine.class.getConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertTrue(!parameter.getName().contains("OpenSanctions"), parameter.getName());
            }
        }
    }

    private OpenSanctionsMatchClient client(Server server) {
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
        return new OpenSanctionsMatchClient(
                "test-key",
                objectMapper,
                URI.create("http://127.0.0.1:" + server.server.getAddress().getPort()),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).executor(executor).build());
    }

    private static SanctionsMatchPort.Query query() {
        return new SanctionsMatchPort.Query("Ada Clear", LocalDate.parse("1990-01-01"), "FR", null, null, new BigDecimal("0.700"));
    }

    private static String person(boolean match) {
        return """
                {"responses":{"q1":{"status":200,"results":[{
                  "id":"NK-1","caption":"Ada Clear","schema":"Person","target":true,"score":0.910,"match":%s,
                  "properties":{"topics":["sanction"],"birthDate":["1990-01-01"],"nationality":["fr"]},
                  "datasets":["fixture"],
                  "explanations":{"name_match":{"score":0.910}}
                }]}}}
                """
                .formatted(match);
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();

        private Server(int failures, int failureStatus, String body) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task);
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/match/default", exchange -> {
                int call = calls.incrementAndGet();
                int status = call <= failures ? failureStatus : 200;
                String payload = status == 200 ? body : "{}";
                byte[] bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
