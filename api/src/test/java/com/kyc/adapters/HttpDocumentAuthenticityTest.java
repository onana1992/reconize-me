package com.kyc.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HttpDocumentAuthenticityTest {

    @Test
    void stoppedServiceFailsFast() {
        HttpDocumentAuthenticity client =
                new HttpDocumentAuthenticity("http://127.0.0.1:1", "tok", 400, new ObjectMapper());
        long started = System.nanoTime();
        AuthenticityUnavailable error =
                assertThrows(AuthenticityUnavailable.class, () -> client.analyze(new byte[] {1, 2, 3}));
        assertTrue(error.getMessage().equals("io") || error.getMessage().equals("timeout"));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 3_000);
    }

    @Test
    void nonSuccessAndOffContractBodiesAreUnavailable() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/analyze", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean down = calls.incrementAndGet() == 1;
            byte[] body = (down ? "{\"error\":\"down\"}" : "{\"ok\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(down ? 500 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpDocumentAuthenticity client = new HttpDocumentAuthenticity(base, "tok", 1000, new ObjectMapper());
            assertEquals(
                    "status",
                    assertThrows(AuthenticityUnavailable.class, () -> client.analyze(new byte[] {1})).getMessage());
            assertEquals(
                    "contract",
                    assertThrows(AuthenticityUnavailable.class, () -> client.analyze(new byte[] {1})).getMessage());
        } finally {
            server.stop(0);
        }
    }
}
