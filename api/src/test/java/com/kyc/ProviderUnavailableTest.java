package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
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
@Import(ProviderUnavailableTest.DownAwsConfig.class)
class ProviderUnavailableTest {

    private static final String KEY = "ky_live_provun01";

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
    void textractFailureBecomesReview() throws Exception {
        IdvSupport.seedLive(organizations, integrations, apiKeys, credits, passwordEncoder, KEY, "Down Co", "down-co");
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String id = created.path("id").asText();
        String token = IdvSupport.token(created);
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.sharpPng());
        var done = IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
        assertEquals("review", done.path("status").asText());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("review"))
                .andExpect(jsonPath("$.decision_reasons[0]").value("provider_unavailable"));
    }

    @TestConfiguration
    static class DownAwsConfig {
        @Bean
        @Primary
        com.kyc.ports.VisionDocumentPort downVision() {
            return (image, mediaType, prompt) -> {
                throw new ProviderUnavailableException("vision down");
            };
        }
    }
}
