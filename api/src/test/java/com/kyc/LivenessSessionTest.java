package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.VerificationLivenessCheck;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.VerificationLivenessCheckRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(LivenessSessionTest.LivenessAwsConfig.class)
class LivenessSessionTest {

    static final AtomicInteger CREATES = new AtomicInteger();
    static final AtomicInteger RESULTS = new AtomicInteger();

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
    private VerificationLivenessCheckRepository checks;

    @Test
    void sandboxDoesNotOpenLiveness() throws Exception {
        CREATES.set(0);
        RESULTS.set(0);
        String key = "ky_test_nolive01";
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, key, "No Live", "no-live");
        var created = IdvSupport.create(mockMvc, key, "{}");
        String token = IdvSupport.token(created);
        mockMvc.perform(post("/v1/flow/" + token + "/liveness/session"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("sandbox_no_liveness"));
        mockMvc.perform(post("/v1/flow/" + token + "/liveness/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"sess-sandbox\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("sandbox_no_liveness"));
        assertEquals(0, CREATES.get());
        assertEquals(0, RESULTS.get());
    }

    @Test
    void reusesAnOpenSession() throws Exception {
        CREATES.set(0);
        String token = reachLiveness("ky_live_reuse001");
        String first = IdvSupport.openLiveness(mockMvc, token).path("session_id").asText();
        String second = IdvSupport.openLiveness(mockMvc, token).path("session_id").asText();
        assertEquals(first, second);
        assertEquals(1, CREATES.get());
        assertEquals("us-east-1", IdvSupport.openLiveness(mockMvc, token).path("region").asText());
    }

    @Test
    void freshSessionReplacesTheOpenOne() throws Exception {
        CREATES.set(0);
        String token = reachLiveness("ky_live_fresh001");
        String first = IdvSupport.openLiveness(mockMvc, token).path("session_id").asText();
        String second = IdvSupport.openLiveness(mockMvc, token, true).path("session_id").asText();
        assertNotEquals(first, second);
        assertEquals(2, CREATES.get());
    }

    @Test
    void mismatchedSessionDoesNotReadResults() throws Exception {
        RESULTS.set(0);
        String token = reachLiveness("ky_live_mismatch1");
        IdvSupport.openLiveness(mockMvc, token);
        mockMvc.perform(post("/v1/flow/" + token + "/liveness/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"other-session\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("liveness_session_mismatch"));
        assertEquals(0, RESULTS.get());
    }

    @Test
    void expiredSessionIsNotBilled() throws Exception {
        RESULTS.set(0);
        String token = reachLiveness("ky_live_expiredlv");
        IdvSupport.openLiveness(mockMvc, token);
        VerificationLivenessCheck check = checks.findAll().get(0);
        check.forceExpiry(Instant.parse("2020-01-01T00:00:00Z"));
        checks.save(check);
        mockMvc.perform(post("/v1/flow/" + token + "/liveness/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"" + check.getSessionId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("liveness_expired"));
        assertEquals(0, RESULTS.get());
        assertEquals(0, checks.countByVerificationIdAndBilledTrue(check.getVerificationId()));
    }

    @Test
    void fourthBilledCheckIsRefusedWithoutAws() throws Exception {
        CREATES.set(0);
        String key = "ky_live_cap00001";
        Reached reached = reach(key);
        String token = reached.token();
        UUID verificationId = reached.verificationId();
        for (int attempt = 1; attempt <= 3; attempt++) {
            VerificationLivenessCheck row = new VerificationLivenessCheck(
                    UUID.randomUUID(),
                    verificationId,
                    attempt,
                    "billed-" + attempt,
                    "us-east-1",
                    Instant.parse("2030-01-01T00:00:00Z"),
                    Instant.parse("2026-10-13T00:00:00Z"));
            row.complete(0.2);
            checks.save(row);
        }
        mockMvc.perform(post("/v1/flow/" + token + "/liveness/session"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("liveness_attempts_exceeded"));
        assertEquals(0, CREATES.get());
        mockMvc.perform(get("/v1/verifications/" + verificationId).header("Authorization", "Bearer " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("declined"))
                .andExpect(jsonPath("$.decision_reasons[0]").value("liveness_fail"))
                .andExpect(jsonPath("$.rules_version").value("bio-1"));
    }

    private String reachLiveness(String key) throws Exception {
        return reach(key).token();
    }

    private Reached reach(String key) throws Exception {
        IdvSupport.seedLive(organizations, integrations, apiKeys, credits, passwordEncoder, key, "Live " + key, key);
        var created = IdvSupport.create(mockMvc, key, "{}");
        String token = IdvSupport.token(created);
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        byte[] document = IdvSupport.sharpPng();
        IdvSupport.captureDocument(mockMvc, token, document);
        var back = IdvSupport.captureDocumentBack(mockMvc, token, document);
        assertEquals("liveness", back.path("next").asText());
        return new Reached(token, UUID.fromString(created.path("id").asText()));
    }

    private record Reached(String token, UUID verificationId) {}

    @TestConfiguration
    static class LivenessAwsConfig {
        @Bean
        @Primary
        com.kyc.ports.VisionDocumentPort cleanVision() {
            return (image, mediaType, prompt) -> {
                try {
                    String name = prompt != null && prompt.contains("Capture side: BACK") ? "quebec-back" : "quebec-clean";
                    return StreamUtils.copyToString(
                            new ClassPathResource("document-ia/" + name + ".json").getInputStream(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
        }

        @Bean
        @Primary
        com.kyc.ports.FaceLivenessClient scriptedLiveness() {
            return new com.kyc.ports.FaceLivenessClient() {
                @Override
                public String createSession(String clientRequestToken) {
                    CREATES.incrementAndGet();
                    return "sess-" + CREATES.get();
                }

                @Override
                public LivenessResult getResults(String sessionId) {
                    RESULTS.incrementAndGet();
                    return new LivenessResult(0.91, "SUCCEEDED", new byte[] {1, 2, 3, 4});
                }
            };
        }

        @Bean
        @Primary
        com.kyc.ports.LivenessCredentialPort testCredentials() {
            return () -> new com.kyc.ports.LivenessCredentialPort.Issued(
                    "AKIA", "secret", "token", Instant.parse("2026-10-13T00:03:00Z"));
        }
    }
}
