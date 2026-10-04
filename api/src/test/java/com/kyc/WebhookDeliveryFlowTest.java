package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.WebhookDelivery;
import com.kyc.enums.WebhookDeliveryStatus;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.WebhookDeliveryRepository;
import com.kyc.services.WebhookDispatcher;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebhookDeliveryFlowTest {

    private static final String KEY_A = "ky_test_whflow_a1";
    private static final String KEY_B = "ky_test_whflow_b1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private WebhookDeliveryRepository deliveries;

    @Autowired
    private WebhookDispatcher dispatcher;

    @Test
    void payloadHasNoMediaUrls() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY_A, "Hook Co", "hook-co");
        mockMvc.perform(put("/v1/webhooks")
                        .header("Authorization", "Bearer " + KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:9/hook\"}"))
                .andExpect(status().isOk());
        completeSandbox();
        WebhookDelivery delivery = deliveries.findAll().get(0);
        String json = delivery.getPayloadJson();
        assertFalse(json.contains("object_key"));
        assertFalse(json.contains("hosted_url"));
        assertFalse(json.contains("/v1/objects"));
        assertTrue(json.contains("\"type\":\"verification.completed\""));
    }

    @Test
    void otherOrgCannotReadOrRetry() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY_A, "Iso A", "iso-a");
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY_B, "Iso B", "iso-b");
        mockMvc.perform(put("/v1/webhooks")
                        .header("Authorization", "Bearer " + KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://localhost:9/hook\"}"))
                .andExpect(status().isOk());
        completeSandbox();
        java.util.UUID eventId = deliveries.findAll().get(0).getId();

        mockMvc.perform(get("/v1/webhooks").header("Authorization", "Bearer " + KEY_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/webhooks/deliveries/" + eventId + "/retry")
                        .header("Authorization", "Bearer " + KEY_B))
                .andExpect(status().isNotFound());
    }

    @Test
    void remote500Then200IsRetried() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = httpServer(hits, new AtomicReference<>(), 500, 200);
        try {
            IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY_A, "Retry Co", "retry-co");
            mockMvc.perform(put("/v1/webhooks")
                            .header("Authorization", "Bearer " + KEY_A)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/hook\"}"))
                    .andExpect(status().isOk());
            completeSandbox();
            dispatcher.dispatchDue();
            WebhookDelivery first = deliveries.findAll().get(0);
            assertEquals(WebhookDeliveryStatus.PENDING, first.getStatus());
            assertEquals(1, first.getAttempt());
            assertEquals(1, hits.get());

            ReflectionTestUtils.setField(first, "nextAttemptAt", Instant.now().minusSeconds(1));
            deliveries.save(first);
            dispatcher.dispatchDue();
            WebhookDelivery second = deliveries.findById(first.getId()).orElseThrow();
            assertEquals(WebhookDeliveryStatus.DELIVERED, second.getStatus());
            assertEquals(2, hits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void manualRetryReusesSameEventIdAndBody() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        AtomicReference<String> lastBody = new AtomicReference<>();
        HttpServer server = httpServer(hits, lastBody, 200, 200);
        try {
            IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY_A, "Replay Co", "replay-co");
            mockMvc.perform(put("/v1/webhooks")
                            .header("Authorization", "Bearer " + KEY_A)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/hook\"}"))
                    .andExpect(status().isOk());
            completeSandbox();
            dispatcher.dispatchDue();
            WebhookDelivery original = deliveries.findAll().get(0);
            String firstBody = lastBody.get();
            java.util.UUID eventId = original.getId();
            assertEquals(WebhookDeliveryStatus.DELIVERED, original.getStatus());

            mockMvc.perform(post("/v1/webhooks/deliveries/" + eventId + "/retry")
                            .header("Authorization", "Bearer " + KEY_A))
                    .andExpect(status().isAccepted());
            dispatcher.dispatchDue();
            WebhookDelivery replayed = deliveries.findById(eventId).orElseThrow();
            assertEquals(eventId, replayed.getId());
            assertEquals(firstBody, lastBody.get());
            assertEquals(2, hits.get());
            assertEquals(WebhookDeliveryStatus.DELIVERED, replayed.getStatus());
        } finally {
            server.stop(0);
        }
    }

    private void completeSandbox() throws Exception {
        var created = IdvSupport.create(mockMvc, KEY_A, "{}");
        String token = IdvSupport.token(created);
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
    }

    private static HttpServer httpServer(AtomicInteger hits, AtomicReference<String> lastBody, int first, int then)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            int n = hits.incrementAndGet();
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int code = n == 1 ? first : then;
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
        server.start();
        return server;
    }
}
