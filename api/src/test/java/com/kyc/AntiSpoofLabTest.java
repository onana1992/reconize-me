package com.kyc;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.entities.ApiKey;
import com.kyc.entities.Integration;
import com.kyc.entities.Organization;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "kyc.anti-spoof.timeout-ms=400")
@AutoConfigureMockMvc
@Transactional
class AntiSpoofLabTest {

    private static final String LIVE = "ky_live_as6bench";
    private static final String TEST = "ky_test_as6bench";
    private static final byte[] JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4};
    private static final String ASSESSMENT =
            """
            {"engine_version":"anti-spoof-2","fusion_version":"identity","forensic_usable":true,\
            "authenticity_score":1.0,"tampering_score":0.0,"presentation_score":0.0,"alerts":[\
            {"code":"splicing","score":0.0,"regions":[]},\
            {"code":"text_alteration","score":0.0,"regions":[]},\
            {"code":"compression_inconsistency","score":0.0,"regions":[]},\
            {"code":"screen_replay","score":0.0,"regions":[]},\
            {"code":"printed_copy","score":0.0,"regions":[]}]}
            """;

    private static final HttpServer SERVER = start();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static volatile boolean stall;
    private static volatile String responseJson = ASSESSMENT;
    private static volatile String lastAuth;
    private static volatile byte[] lastBody;

    @DynamicPropertySource
    static void antiSpoof(DynamicPropertyRegistry registry) {
        registry.add("kyc.anti-spoof.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("kyc.anti-spoof.token", () -> "test-token");
    }

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

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
    private VerificationRepository verifications;

    @BeforeEach
    void seed() {
        stall = false;
        responseJson = ASSESSMENT;
        CALLS.set(0);
        lastAuth = null;
        lastBody = null;
        seed(LIVE, IntegrationMode.LIVE, "Anti-spoof Live", "as-live");
        seed(TEST, IntegrationMode.TEST, "Anti-spoof Test", "as-test");
    }

    @Test
    void missingKeyIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/v1/anti-spoof/analyze").file(jpeg()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
        assertEquals(0, CALLS.get());
    }

    @Test
    void liveKeyReturnsAssessmentWithoutDecision() throws Exception {
        long before = verifications.count();
        mockMvc.perform(multipart("/v1/anti-spoof/analyze").file(jpeg()).header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("auth-1"))
                .andExpect(jsonPath("$.providerCalled").value(true))
                .andExpect(jsonPath("$.engineVersion").value("anti-spoof-2"))
                .andExpect(jsonPath("$.decision.issue").value("PASS"))
                .andExpect(jsonPath("$.decision.rulesVersion").value("auth-1"))
                .andExpect(jsonPath("$.decision.signals[0].code").value("document_authentic"))
                .andExpect(jsonPath("$.decision.signals[0].outcome").value("PASS"))
                .andExpect(jsonPath("$.decision.signals[0].score").value(1.0))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.assessment.engine_version").value("anti-spoof-2"))
                .andExpect(jsonPath("$.assessment.fusion_version").value("identity"))
                .andExpect(jsonPath("$.assessment.alerts.length()").value(5));
        assertEquals(before, verifications.count());
        assertEquals(1, CALLS.get());
        assertEquals("Bearer test-token", lastAuth);
        JsonNode sent = new ObjectMapper().readTree(lastBody);
        assertEquals("FRONT", sent.get("side").asText());
        assertEquals("image/jpeg", sent.get("media_type").asText());
        assertFalse(sent.has("hints"));
        UUID.fromString(sent.get("request_id").asText());
        UUID.fromString(sent.get("verification_id").asText());
        assertArrayEquals(JPEG, Base64.getDecoder().decode(sent.get("image_base64").asText()));
    }

    @Test
    void closedScreenReplayStaysReview() throws Exception {
        responseJson =
                """
                {"engine_version":"anti-spoof-2","fusion_version":"identity","forensic_usable":true,\
                "authenticity_score":0.1,"tampering_score":0.0,"presentation_score":0.9,"alerts":[\
                {"code":"splicing","score":0.0,"regions":[]},\
                {"code":"text_alteration","score":0.0,"regions":[]},\
                {"code":"compression_inconsistency","score":0.0,"regions":[]},\
                {"code":"screen_replay","score":0.9,"regions":[]},\
                {"code":"printed_copy","score":0.0,"regions":[]}]}
                """;
        long before = verifications.count();
        mockMvc.perform(multipart("/v1/anti-spoof/analyze").file(jpeg()).header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.issue").value("REVIEW"))
                .andExpect(jsonPath("$.decision.rulesVersion").value("auth-1"))
                .andExpect(jsonPath("$.decision.reasons[0]").value("tampering_suspected"))
                .andExpect(jsonPath("$.decision.signals[0].code").value("screen_replay_detected"))
                .andExpect(jsonPath("$.decision.signals[0].outcome").value("FAIL"))
                .andExpect(jsonPath("$.decision.signals[0].score").value(0.9));
        assertEquals(before, verifications.count());
    }

    @Test
    void testKeyDoesNotCallTheService() throws Exception {
        mockMvc.perform(multipart("/v1/anti-spoof/analyze").file(jpeg()).header("Authorization", "Bearer " + TEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("sandbox_no_anti_spoof"));
        assertEquals(0, CALLS.get());
    }

    @Test
    void timeoutReturnsUnavailableEnvelope() throws Exception {
        stall = true;
        mockMvc.perform(multipart("/v1/anti-spoof/analyze").file(jpeg()).header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.pipeline").value("auth-1"))
                .andExpect(jsonPath("$.providerCalled").value(true))
                .andExpect(jsonPath("$.engineVersion").value(nullValue()))
                .andExpect(jsonPath("$.assessment").value(nullValue()))
                .andExpect(jsonPath("$.decision.issue").value("REVIEW"))
                .andExpect(jsonPath("$.decision.reasons[0]").value("authenticity_unavailable"))
                .andExpect(jsonPath("$.decision.signals[0].code").value("authenticity_unavailable"))
                .andExpect(jsonPath("$.decision.signals[0].outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.code").value("authenticity_unavailable"));
        assertEquals(1, CALLS.get());
    }

    @Test
    void emptyFileIsRejectedBeforeTheCall() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);
        mockMvc.perform(multipart("/v1/anti-spoof/analyze").file(empty).header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"))
                .andExpect(jsonPath("$.error.details[0].field").value("file"));
        assertEquals(0, CALLS.get());
    }

    @Test
    void operationIsListedWithoutChangingVerificationsOrFlow() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/anti-spoof/analyze']").exists())
                .andExpect(jsonPath("$.paths['/v1/anti-spoof/analyze'].post.security[0]['bearer-api-key']").exists())
                .andExpect(jsonPath("$.paths['/v1/verifications']").exists())
                .andExpect(jsonPath("$.paths['/v1/flow/{token}']").exists());
    }

    private void seed(String rawKey, IntegrationMode mode, String orgName, String slug) {
        Instant now = Instant.parse("2026-11-05T12:00:00Z");
        UUID organizationId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        organizations.save(new Organization(organizationId, orgName, slug, now));
        integrations.save(new Integration(
                integrationId, organizationId, ProductCode.IDENTITY, mode, mode.name(), now));
        apiKeys.save(new ApiKey(
                UUID.randomUUID(),
                organizationId,
                integrationId,
                rawKey.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(rawKey),
                now));
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "card.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG);
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/analyze", AntiSpoofLabTest::handle);
            server.setExecutor(Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, "anti-spoof-test");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        exchange.getRequestBody().transferTo(body);
        lastBody = body.toByteArray();
        lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
        CALLS.incrementAndGet();
        try {
            if (stall) {
                Thread.sleep(2_000);
            }
            byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }
}
