package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.CompareFacesClient;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(UnsupportedLiveTest.OntarioAwsConfig.class)
class UnsupportedLiveTest {

    private static final String KEY = "ky_live_unsup01xx";
    static final AtomicInteger COMPARE_CALLS = new AtomicInteger();

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
    void ontarioSkipsCompareFaces() throws Exception {
        COMPARE_CALLS.set(0);
        IdvSupport.seedLive(organizations, integrations, apiKeys, credits, passwordEncoder, KEY, "Unsup Co", "unsup-co");
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String id = created.path("id").asText();
        String token = IdvSupport.token(created);
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.sharpPng());
        var done = IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
        assertEquals("review", done.path("status").asText());
        assertEquals(0, COMPARE_CALLS.get());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("review"))
                .andExpect(jsonPath("$.rules_version").value("vision-1"))
                .andExpect(jsonPath("$.decision_reasons[0]").value("DOCUMENT_UNKNOWN"));
    }

    @TestConfiguration
    static class OntarioAwsConfig {
        @Bean
        @Primary
        com.kyc.ports.VisionDocumentPort unknownVision() {
            return (image, mediaType, prompt) ->
                    """
                    {"detection":"DOCUMENT_PRESENT","classification":{"code":"MARS_PASSPORT","confidence":0.99}}
                    """;
        }

        @Bean
        @Primary
        CompareFacesClient countingCompareFacesClient() {
            return (document, selfie) -> {
                COMPARE_CALLS.incrementAndGet();
                return 0.96;
            };
        }
    }
}
