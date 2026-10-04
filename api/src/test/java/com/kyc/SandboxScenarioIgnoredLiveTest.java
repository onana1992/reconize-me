package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@Import(SandboxScenarioIgnoredLiveTest.MismatchAwsConfig.class)
class SandboxScenarioIgnoredLiveTest {

    private static final String KEY = "ky_live_scenign01";

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
    void liveIgnoresApprovedSandboxScenario() throws Exception {
        IdvSupport.seedLive(organizations, integrations, apiKeys, credits, passwordEncoder, KEY, "Scen Co", "scen-co");
        var created = IdvSupport.create(mockMvc, KEY, "{\"metadata\":{\"sandbox_scenario\":\"approved\"}}");
        String id = created.path("id").asText();
        String token = IdvSupport.token(created);
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        var done = IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
        assertEquals("declined", done.path("status").asText());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("declined"))
                .andExpect(jsonPath("$.decision_reasons[0]").value("face_match_fail"))
                .andExpect(jsonPath("$.rules_version").value("m5-1"));
    }

    @TestConfiguration
    static class MismatchAwsConfig {
        @Bean
        @Primary
        com.kyc.ports.AnalyzeIdClient fixtureAnalyzeIdClient() {
            return image -> M5FixtureAwsConfig.fixtureJson();
        }

        @Bean
        @Primary
        com.kyc.ports.CompareFacesClient lowMatchClient() {
            return (document, selfie) -> 0.31;
        }
    }
}
