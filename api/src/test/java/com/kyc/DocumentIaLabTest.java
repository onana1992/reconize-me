package com.kyc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.kyc.services.documentia.SchemaRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentIaLabTest {

    private static final String LIVE = "ky_live_v0bench1";
    private static final String TEST = "ky_test_v0bench1";

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
        seed(LIVE, IntegrationMode.LIVE, "Lab Live", "lab-live");
        seed(TEST, IntegrationMode.TEST, "Lab Test", "lab-test");
    }

    @Test
    void missingBearerIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(jpeg()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void testKeyIsForbiddenWithoutCallingVision() throws Exception {
        long before = verifications.count();
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(jpeg())
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("sandbox_no_vision"));
        org.junit.jupiter.api.Assertions.assertEquals(before, verifications.count());
    }

    @Test
    void liveKeyReturnsEmptyEnvelope() throws Exception {
        long before = verifications.count();
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(jpeg())
                        .param("until", "quality")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("vision-1"))
                .andExpect(jsonPath("$.stoppedAt").value(nullValue()))
                .andExpect(jsonPath("$.provider").value("vision_llm"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.schemaVersionId").value(nullValue()))
                .andExpect(jsonPath("$.quality").value(nullValue()))
                .andExpect(jsonPath("$.rawModel").value(nullValue()))
                .andExpect(jsonPath("$.parsed").value(nullValue()))
                .andExpect(jsonPath("$.fields").value(nullValue()))
                .andExpect(jsonPath("$.mrz").value(nullValue()))
                .andExpect(jsonPath("$.validation").value(nullValue()))
                .andExpect(jsonPath("$.indicators").value(nullValue()))
                .andExpect(jsonPath("$.scores").value(nullValue()))
                .andExpect(jsonPath("$.decision").value(nullValue()));
        org.junit.jupiter.api.Assertions.assertEquals(before, verifications.count());
    }

    @Test
    void unknownUntilIsRejected() throws Exception {
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(jpeg())
                        .param("until", "score")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"))
                .andExpect(jsonPath("$.error.details[0].field").value("until"));
    }

    @Test
    void catalogIsEmptyAndListedInOpenApi() throws Exception {
        mockMvc.perform(get("/v1/document-ia/catalog").header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas", hasSize(1)))
                .andExpect(jsonPath("$.schemas[0].code").value("QUEBEC_DRIVER_LICENSE"))
                .andExpect(jsonPath("$.schemas[0].country").value("CA"))
                .andExpect(jsonPath("$.schemas[0].documentType").value("DRIVING_LICENSE"))
                .andExpect(jsonPath("$.schemas[0].side").value("FRONT"))
                .andExpect(jsonPath("$.schemas[0].version").value("2024"))
                .andExpect(jsonPath("$.schemas[0].issuingJurisdiction").value("QC"));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/document-ia/analyze']").exists())
                .andExpect(jsonPath("$.paths['/v1/document-ia/catalog']").exists())
                .andExpect(jsonPath("$.paths['/v1/document-ia/fixtures/{name}']").exists());
    }

    @Test
    void quebecFixtureFillsParsedBlock() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-driver-license")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("vision-1"))
                .andExpect(jsonPath("$.stoppedAt").value("parse"))
                .andExpect(jsonPath("$.provider").value("vision_llm"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.parsed.classification.code").value("QUEBEC_DRIVER_LICENSE"))
                .andExpect(jsonPath("$.parsed.classification.country").value("CA"))
                .andExpect(jsonPath("$.parsed.classification.documentType").value("DRIVING_LICENSE"))
                .andExpect(jsonPath("$.parsed.classification.side").value("FRONT"))
                .andExpect(jsonPath("$.schemaVersionId").value(SchemaRegistry.QUEBEC_VERSION_ID.toString()))
                .andExpect(jsonPath("$.parsed.classification.issuingJurisdiction").value("QC"))
                .andExpect(jsonPath("$.parsed.classification.version").value("2024"))
                .andExpect(jsonPath("$.parsed.indicators").isEmpty())
                .andExpect(jsonPath("$.parsed.fields", hasSize(1)))
                .andExpect(jsonPath("$.parsed.quality.readable").value(true))
                .andExpect(jsonPath("$.parsed.quality.tilted").value(true))
                .andExpect(jsonPath("$.parsed.zones[1].text").value("HORS"))
                .andExpect(jsonPath("$.parsed.zones[1].boundingBox").value(nullValue()))
                .andExpect(jsonPath("$.parsed.zones[0].bboxPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.parsed.fields[0].value").value("TREMBLAY"))
                .andExpect(jsonPath("$.parsed.fields[0].normalizedValue").doesNotExist())
                .andExpect(jsonPath("$.parsed.fields[0].checkDigit").doesNotExist())
                .andExpect(jsonPath("$.parsed.qualityScore").doesNotExist());
    }

    @Test
    void unknownCodeIsForcedToUnknown() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/unknown-code")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("parse"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.parsed.classification.code").value("UNKNOWN"))
                .andExpect(jsonPath("$.parsed.classification.country").value("ZZ"))
                .andExpect(jsonPath("$.parsed.classification.documentType").value("UNKNOWN"))
                .andExpect(jsonPath("$.parsed.classification.side").value("UNKNOWN"))
                .andExpect(jsonPath("$.schemaVersionId").value(nullValue()))
                .andExpect(jsonPath("$.parsed.fields").isEmpty())
                .andExpect(jsonPath("$.parsed.classification.version").value(nullValue()));
    }

    @Test
    void unknownVersionIsNotBoundToAnEdition() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-unexpected-version")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersionId").value(nullValue()))
                .andExpect(jsonPath("$.parsed.classification.code").value("QUEBEC_DRIVER_LICENSE"))
                .andExpect(jsonPath("$.parsed.classification.version").value("1999"))
                .andExpect(jsonPath("$.parsed.indicators[0]").value("unexpected_document_structure"));
    }

    @Test
    void lowClassConfidenceKeepsTheCode() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-low-confidence")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsed.classification.code").value("QUEBEC_DRIVER_LICENSE"))
                .andExpect(jsonPath("$.parsed.indicators[0]").value("DOCUMENT_UNKNOWN"));
    }

    @Test
    void invalidFixtureIsUnprocessable() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/invalid")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("invalid_model_json"));
    }

    @Test
    void missingFixtureIsNotFound() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/does-not-exist")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    private void seed(String rawKey, IntegrationMode mode, String orgName, String slug) {
        Instant now = Instant.parse("2026-10-06T12:00:00Z");
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

    private static MockMultipartFile jpeg() throws Exception {
        return new MockMultipartFile("file", "doc.jpg", MediaType.IMAGE_JPEG_VALUE, IdvSupport.goodJpeg());
    }
}
