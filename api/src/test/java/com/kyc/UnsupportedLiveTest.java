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
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        var done = IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
        assertEquals("declined", done.path("status").asText());
        assertEquals(0, COMPARE_CALLS.get());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("declined"))
                .andExpect(jsonPath("$.decision_reasons[0]").value("unsupported_document"));
    }

    @TestConfiguration
    static class OntarioAwsConfig {
        @Bean
        @Primary
        AnalyzeIdClient ontarioAnalyzeIdClient() {
            return image ->
                    """
                    {"IdentityDocuments":[{"IdentityDocumentFields":[
                      {"Type":{"Text":"ID_TYPE"},"ValueDetection":{"Text":"DRIVER LICENSE"}},
                      {"Type":{"Text":"STATE_NAME"},"ValueDetection":{"Text":"ONTARIO"}},
                      {"Type":{"Text":"COUNTRY"},"ValueDetection":{"Text":"CANADA"}},
                      {"Type":{"Text":"FIRST_NAME"},"ValueDetection":{"Text":"A"}},
                      {"Type":{"Text":"LAST_NAME"},"ValueDetection":{"Text":"B"}},
                      {"Type":{"Text":"DATE_OF_BIRTH"},"ValueDetection":{"Text":"1990-01-01"}},
                      {"Type":{"Text":"EXPIRATION_DATE"},"ValueDetection":{"Text":"2030-01-01"}},
                      {"Type":{"Text":"DOCUMENT_NUMBER"},"ValueDetection":{"Text":"X"}}
                    ]}]}
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
