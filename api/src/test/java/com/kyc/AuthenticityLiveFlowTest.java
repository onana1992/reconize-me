package com.kyc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.entities.VerificationAuthenticityAssessment;
import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.ports.VisionDocumentPort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.VerificationAuthenticityAssessmentRepository;
import com.kyc.repositories.VerificationMediaRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@SpringBootTest(properties = {"kyc.anti-spoof.enabled=true", "kyc.anti-spoof.timeout-ms=400"})
@AutoConfigureMockMvc
@Transactional
@Import(AuthenticityLiveFlowTest.LiveConfig.class)
class AuthenticityLiveFlowTest {

    private static final String KEY = "ky_live_authflow1";
    private static final String PASS =
            """
            {"engine_version":"anti-spoof-2","fusion_version":"identity","forensic_usable":true,\
            "authenticity_score":1.0,"tampering_score":0.0,"presentation_score":0.0,"alerts":[\
            {"code":"splicing","score":0.0,"regions":[]},\
            {"code":"text_alteration","score":0.0,"regions":[]},\
            {"code":"compression_inconsistency","score":0.0,"regions":[]},\
            {"code":"screen_replay","score":0.0,"regions":[]},\
            {"code":"printed_copy","score":0.0,"regions":[]}]}
            """;
    private static final String SCREEN =
            """
            {"engine_version":"anti-spoof-2","fusion_version":"identity","forensic_usable":true,\
            "authenticity_score":0.1,"tampering_score":0.0,"presentation_score":0.9,"alerts":[\
            {"code":"splicing","score":0.0,"regions":[]},\
            {"code":"text_alteration","score":0.0,"regions":[]},\
            {"code":"compression_inconsistency","score":0.0,"regions":[]},\
            {"code":"screen_replay","score":0.9,"regions":[{"x":0.1,"y":0.1,"w":0.4,"h":0.4}]},\
            {"code":"printed_copy","score":0.0,"regions":[]}]}
            """;

    private static final HttpServer SERVER = start();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static volatile int statusCode = 200;
    private static volatile String frontJson = PASS;
    private static volatile String backJson = PASS;
    private static volatile byte[] lastImage = new byte[0];
    private static volatile String lastSide = "";
    private static volatile String lastVerificationId = "";

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
    private CreditAccountRepository credits;

    @Autowired
    private VerificationAuthenticityAssessmentRepository assessments;

    @Autowired
    private VerificationMediaRepository media;

    @Autowired
    private ObjectStoragePort objectStorage;

    @BeforeEach
    void reset() {
        CALLS.set(0);
        statusCode = 200;
        frontJson = PASS;
        backJson = PASS;
        lastImage = new byte[0];
        lastSide = "";
        lastVerificationId = "";
        IdvSupport.seedLive(
                organizations, integrations, apiKeys, credits, passwordEncoder, KEY, "Auth Live Co", "auth-live-co");
    }

    @Test
    void liveStoresOneRowPerSideFromStoredBytes() throws Exception {
        byte[] jpeg = IdvSupport.largeSharpJpeg();
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        UUID id = UUID.fromString(created.path("id").asText());
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        var front = IdvSupport.captureDocument(mockMvc, token, jpeg);
        assertEquals("capture_document_back", front.path("next").asText());
        VerificationAuthenticityAssessment stored =
                assessments.findByVerificationIdAndSide(id, "FRONT").orElseThrow();
        assertEquals("anti-spoof-2", stored.getEngineVersion());
        var accepted = media.findByVerificationIdAndKindAndAttempt(
                        id, com.kyc.enums.MediaKind.DOCUMENT, 1)
                .orElseThrow();
        byte[] storedBytes = objectStorage.read(accepted.getObjectKey());
        assertArrayEquals(storedBytes, lastImage);
        assertEquals(jpeg.length, lastImage.length);
        assertEquals(id.toString(), lastVerificationId);
        assertEquals("FRONT", lastSide);

        var back = IdvSupport.captureDocumentBack(mockMvc, token, jpeg);
        assertEquals("liveness", back.path("next").asText());
        assertEquals("anti-spoof-2", assessments.findByVerificationIdAndSide(id, "BACK").orElseThrow().getEngineVersion());
        assertEquals(2, CALLS.get());

        var done = IdvSupport.completeLiveness(mockMvc, token);
        assertEquals("approved", done.path("status").asText());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("approved"))
                .andExpect(jsonPath("$.signals[*].code").value(hasItem("document_authentic")))
                .andExpect(jsonPath("$.authenticity[0].engine_version").value("anti-spoof-2"))
                .andExpect(jsonPath("$.authenticity[0].alerts[0].code").value("splicing"));
    }

    @Test
    void worseSideWinsAndKeepsRectanglesOnRead() throws Exception {
        backJson = SCREEN;
        byte[] jpeg = IdvSupport.sharpPng();
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, jpeg);
        var back = IdvSupport.captureDocumentBack(mockMvc, token, jpeg);
        assertEquals("review", back.path("status").asText());
        assertEquals("done", back.path("next").asText());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("review"))
                .andExpect(jsonPath("$.decision_reasons[0]").value("tampering_suspected"))
                .andExpect(jsonPath("$.rules_version").value("auth-1"))
                .andExpect(jsonPath("$.signals[*].code").value(hasItem("screen_replay_detected")))
                .andExpect(jsonPath("$.authenticity[0].side").value("BACK"))
                .andExpect(jsonPath("$.authenticity[0].alerts[3].regions[0].w").value(0.4));
    }

    @Test
    void downServiceReviewsUnavailable() throws Exception {
        statusCode = 500;
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.sharpPng());
        var back = IdvSupport.captureDocumentBack(mockMvc, token, IdvSupport.sharpPng());
        assertEquals("review", back.path("status").asText());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("review"))
                .andExpect(jsonPath("$.decision_reasons[0]").value("authenticity_unavailable"))
                .andExpect(jsonPath("$.signals[*].code").value(hasItem("authenticity_unavailable")))
                .andExpect(jsonPath("$.signals[*].code").value(not(hasItem("authenticity_stub_pass"))))
                .andExpect(jsonPath("$.rules_version").value("auth-1"));
    }

    @Test
    void qualityRejectDoesNotCallThePort() throws Exception {
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        var rejected = IdvSupport.captureDocument(mockMvc, token, IdvSupport.tinyJpeg());
        org.junit.jupiter.api.Assertions.assertFalse(rejected.path("accepted").asBoolean());
        assertEquals(0, CALLS.get());
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/analyze", AuthenticityLiveFlowTest::handle);
            server.setExecutor(Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, "anti-spoof-flow-test");
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
        CALLS.incrementAndGet();
        try {
            JsonNode sent = new ObjectMapper().readTree(body.toByteArray());
            lastSide = sent.path("side").asText();
            lastVerificationId = sent.path("verification_id").asText();
            lastImage = Base64.getDecoder().decode(sent.path("image_base64").asText());
            byte[] response = ("BACK".equals(lastSide) ? backJson : frontJson).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, statusCode == 200 ? response.length : 2);
            if (statusCode == 200) {
                exchange.getResponseBody().write(response);
            } else {
                exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            exchange.close();
        }
    }

    @TestConfiguration
    static class LiveConfig {
        @Bean
        @Primary
        VisionDocumentPort cleanVision() {
            return (image, mediaType, prompt) -> {
                try {
                    String name = prompt != null && prompt.contains("Capture side: BACK")
                            ? "quebec-back"
                            : "quebec-clean";
                    return StreamUtils.copyToString(
                            new ClassPathResource("document-ia/" + name + ".json").getInputStream(),
                            StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
        }

        @Bean
        @Primary
        AnalyzeIdClient unusedAnalyzeId() {
            return image -> {
                throw new com.kyc.ports.ProviderUnavailableException("AnalyzeID must stay off the live path");
            };
        }

        @Bean
        @Primary
        CompareFacesClient passingFaces() {
            return (document, selfie) -> 0.96;
        }

        @Bean
        @Primary
        com.kyc.ports.FaceLivenessClient passingLiveness() {
            return new PassingLiveness();
        }

        static final class PassingLiveness implements com.kyc.ports.FaceLivenessClient {
            @Override
            public String createSession(String clientRequestToken) {
                return "session";
            }

            @Override
            public LivenessResult getResults(String sessionId) {
                return new LivenessResult(0.91, "SUCCEEDED", new byte[] {9, 8, 7, 6});
            }
        }

        @Bean
        @Primary
        com.kyc.ports.LivenessCredentialPort testCredentials() {
            return () -> new com.kyc.ports.LivenessCredentialPort.Issued(
                    "AKIA", "secret", "token", Instant.parse("2026-10-13T00:00:00Z"));
        }
    }
}
