package com.kyc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.DocumentAuthenticityPort;
import com.kyc.ports.VisionDocumentPort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AuthenticityStubFlowTest.StubLiveConfig.class)
class AuthenticityStubFlowTest {

    private static final String KEY = "ky_live_authstub1";
    static final AtomicInteger AUTH_CALLS = new AtomicInteger();

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

    @Test
    void liveFlagOffNeverCallsHttpAndStubs() throws Exception {
        AUTH_CALLS.set(0);
        IdvSupport.seedLive(
                organizations, integrations, apiKeys, credits, passwordEncoder, KEY, "Auth Stub Co", "auth-stub-co");
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.sharpPng());
        IdvSupport.captureDocumentBack(mockMvc, token, IdvSupport.sharpPng());
        var done = IdvSupport.completeLiveness(mockMvc, token);
        assertEquals("approved", done.path("status").asText());
        assertEquals(0, AUTH_CALLS.get());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("approved"))
                .andExpect(jsonPath("$.signals[*].code").value(hasItem("authenticity_stub_pass")))
                .andExpect(jsonPath("$.signals[*].code").value(not(hasItem("authenticity_unavailable"))))
                .andExpect(jsonPath("$.authenticity[0].engine_version").value("stub"))
                .andExpect(jsonPath("$.authenticity[1].engine_version").value("stub"));
    }

    @TestConfiguration
    static class StubLiveConfig {
        @Bean
        @Primary
        DocumentAuthenticityPort countingAuthenticity() {
            return jpeg -> {
                AUTH_CALLS.incrementAndGet();
                throw new com.kyc.adapters.AuthenticityUnavailable("test");
            };
        }

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
