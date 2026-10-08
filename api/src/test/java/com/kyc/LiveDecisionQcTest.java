package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.VisionDocumentPort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.VerificationDocumentAnalysisRepository;
import com.kyc.services.documentia.SchemaRegistry;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(LiveDecisionQcTest.VisionLiveConfig.class)
class LiveDecisionQcTest {

    private static final String KEY = "ky_live_livedec01";
    private static final AtomicInteger ANALYZE_CALLS = new AtomicInteger();

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
    private VerificationDocumentAnalysisRepository analyses;

    @Test
    void liveMatchesTheLabAndStoresTheAnalysis() throws Exception {
        ANALYZE_CALLS.set(0);
        byte[] document = IdvSupport.sharpPng();
        IdvSupport.seedLive(organizations, integrations, apiKeys, credits, passwordEncoder, KEY, "Live QC Co", "live-qc-co");

        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(new MockMultipartFile("file", "sharp.png", MediaType.IMAGE_PNG_VALUE, document))
                        .param("until", "decide")
                        .header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.verificationDecision").value("APPROVED"))
                .andExpect(jsonPath("$.decision.rulesVersion").value("vision-1"));

        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        var front = IdvSupport.captureDocument(mockMvc, token, document);
        assertEquals("capture_document_back", front.path("next").asText());
        IdvSupport.captureDocumentBack(mockMvc, token, document);
        var done = IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
        assertEquals("approved", done.path("status").asText());
        assertEquals(0, ANALYZE_CALLS.get());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("approved"))
                .andExpect(jsonPath("$.rules_version").value("vision-1"))
                .andExpect(jsonPath("$.extracted_identity.document_type").value("driving_license"))
                .andExpect(jsonPath("$.extracted_identity.document_country").value("CA"))
                .andExpect(jsonPath("$.extracted_identity.issuing_jurisdiction").value("QC"))
                .andExpect(jsonPath("$.extracted_identity.first_name").value("MARIE"))
                .andExpect(jsonPath("$.extracted_identity.last_name").value("TREMBLAY"));

        var stored = analyses.findByVerificationIdAndSide(UUID.fromString(id), "FRONT").orElseThrow();
        var back = analyses.findByVerificationIdAndSide(UUID.fromString(id), "BACK").orElseThrow();
        assertEquals(SchemaRegistry.QUEBEC_BACK_VERSION_ID, back.getSchemaVersionId());
        assertEquals(SchemaRegistry.QUEBEC_VERSION_ID, stored.getSchemaVersionId());
        assertEquals("vision_llm", stored.getProvider());
        assertEquals("fake", stored.getModelId());
        assertTrue(stored.getFieldsJson().contains("TREMBLAY"));
        assertTrue(stored.getScoresJson().contains("overallScore"));
    }

    @TestConfiguration
    static class VisionLiveConfig {
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
        AnalyzeIdClient countingAnalyzeIdClient() {
            return image -> {
                ANALYZE_CALLS.incrementAndGet();
                throw new com.kyc.ports.ProviderUnavailableException("AnalyzeID must stay off the live path");
            };
        }

        @Bean
        @Primary
        CompareFacesClient passingFaces() {
            return (document, selfie) -> 0.96;
        }
    }
}
