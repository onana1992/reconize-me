package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.DocumentAuthenticityPort;
import com.kyc.repositories.ApiKeyRepository;
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

@SpringBootTest(properties = "kyc.anti-spoof.enabled=true")
@AutoConfigureMockMvc
@Transactional
@Import(AuthenticitySandboxTest.CountingAuthenticity.class)
class AuthenticitySandboxTest {

    private static final String KEY = "ky_test_auths801";
    static final AtomicInteger CALLS = new AtomicInteger();

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

    @Test
    void printedScenarioDeclinesWithoutHttp() throws Exception {
        CALLS.set(0);
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Auth Sandbox", "auth-sandbox");
        var created = IdvSupport.create(mockMvc, KEY, "{\"metadata\":{\"sandbox_scenario\":\"printed\"}}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.decision").value("declined"))
                .andExpect(jsonPath("$.decision_reasons[0]").value("printed_copy_detected"));
        org.junit.jupiter.api.Assertions.assertEquals(0, CALLS.get());
    }

    @TestConfiguration
    static class CountingAuthenticity {
        @Bean
        @Primary
        DocumentAuthenticityPort countingAuthenticity() {
            return jpeg -> {
                CALLS.incrementAndGet();
                throw new com.kyc.adapters.AuthenticityUnavailable("test");
            };
        }
    }
}
