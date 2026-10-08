package com.kyc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
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
                        .param("until", "vision")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("sandbox_no_vision"));
        org.junit.jupiter.api.Assertions.assertEquals(before, verifications.count());
    }

    @Test
    void readableImageStopsAtQualityWithoutCallingVision() throws Exception {
        long before = verifications.count();
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(png("sharp.png", sampledChecker(640, 480, 0x000000, 0xFFFFFF)))
                        .param("until", "quality")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("vision-1"))
                .andExpect(jsonPath("$.stoppedAt").value("quality"))
                .andExpect(jsonPath("$.provider").value("vision_llm"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.schemaVersionId").value(nullValue()))
                .andExpect(jsonPath("$.quality.readable").value(true))
                .andExpect(jsonPath("$.quality.blur").value(false))
                .andExpect(jsonPath("$.quality.tooSmall").value(false))
                .andExpect(jsonPath("$.quality.glare").value(false))
                .andExpect(jsonPath("$.quality.reason").value(nullValue()))
                .andExpect(jsonPath("$.rawModel").value(nullValue()))
                .andExpect(jsonPath("$.parsed").value(nullValue()))
                .andExpect(jsonPath("$.decision").value(nullValue()));
        org.junit.jupiter.api.Assertions.assertEquals(before, verifications.count());
    }

    @Test
    void readableImageCallsVisionAndParsesTheFixture() throws Exception {
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(png("sharp.png", sampledChecker(640, 480, 0x000000, 0xFFFFFF)))
                        .param("until", "vision")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("vision"))
                .andExpect(jsonPath("$.provider").value("vision_llm"))
                .andExpect(jsonPath("$.providerCalled").value(true))
                .andExpect(jsonPath("$.quality.readable").value(true))
                .andExpect(jsonPath("$.rawModel.classification.code").value("QUEBEC_DRIVER_LICENSE"))
                .andExpect(jsonPath("$.rawModel.classification.country").value("FR"))
                .andExpect(jsonPath("$.parsed.classification.code").value("QUEBEC_DRIVER_LICENSE"))
                .andExpect(jsonPath("$.parsed.classification.country").value("CA"))
                .andExpect(jsonPath("$.schemaVersionId").value(SchemaRegistry.QUEBEC_VERSION_ID.toString()))
                .andExpect(jsonPath("$.decision").value(nullValue()))
                .andExpect(jsonPath("$.indicators").value(nullValue()));
    }

    @Test
    void unreadableImageDoesNotCallVision() throws Exception {
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "tiny.jpg", MediaType.IMAGE_JPEG_VALUE, IdvSupport.tinyJpeg()))
                        .param("until", "vision")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("quality"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.rawModel").value(nullValue()))
                .andExpect(jsonPath("$.quality.reason").value("TOO_SMALL"));
    }

    @Test
    void tinyImageStopsForQuality() throws Exception {
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "tiny.jpg", MediaType.IMAGE_JPEG_VALUE, IdvSupport.tinyJpeg()))
                        .param("until", "quality")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("quality"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.quality.readable").value(false))
                .andExpect(jsonPath("$.quality.tooSmall").value(true))
                .andExpect(jsonPath("$.quality.reason").value("TOO_SMALL"))
                .andExpect(jsonPath("$.decision").value(nullValue()));
    }

    @Test
    void unreadableBytesAreRejected() throws Exception {
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, new byte[] {1, 2, 3, 4}))
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"))
                .andExpect(jsonPath("$.error.details[0].field").value("file"));
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
                .andExpect(jsonPath("$.schemas", hasSize(5)))
                .andExpect(jsonPath("$.schemas[?(@.code == 'CANADA_PERMANENT_RESIDENT')].side").value(hasItem("FRONT")))
                .andExpect(jsonPath("$.schemas[?(@.code == 'CANADA_PERMANENT_RESIDENT')].side").value(hasItem("BACK")))
                .andExpect(jsonPath("$.schemas[?(@.code == 'CANADA_PERMANENT_RESIDENT')].documentType").value(hasItem("RESIDENCE_PERMIT")))
                .andExpect(jsonPath("$.schemas[?(@.side == 'BACK')].code").value(hasItem("QUEBEC_DRIVER_LICENSE")))
                .andExpect(jsonPath("$.schemas[?(@.code == 'QUEBEC_DRIVER_LICENSE')].country").value(hasItem("CA")))
                .andExpect(jsonPath("$.schemas[?(@.code == 'QUEBEC_DRIVER_LICENSE')].documentType").value(hasItem("DRIVING_LICENSE")))
                .andExpect(jsonPath("$.schemas[?(@.code == 'QUEBEC_DRIVER_LICENSE')].side").value(hasItem("FRONT")))
                .andExpect(jsonPath("$.schemas[?(@.code == 'QUEBEC_DRIVER_LICENSE')].version").value(hasItem("2024")))
                .andExpect(jsonPath("$.schemas[?(@.code == 'QUEBEC_DRIVER_LICENSE')].issuingJurisdiction").value(hasItem("QC")))
                .andExpect(jsonPath("$.schemas[?(@.code == 'PASSPORT_TD3')].documentType").value(hasItem("PASSPORT")));
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

    @Test
    void quebecFixtureNormalizesEachReadField() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-driver-license")
                        .param("until", "normalize")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("normalize"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.decision").value(nullValue()))
                .andExpect(jsonPath("$.fields[?(@.field == 'lastName')].value").value(hasItem("TREMBLAY")))
                .andExpect(jsonPath("$.fields[?(@.field == 'lastName')].normalizedValue").value(hasItem("TREMBLAY")))
                .andExpect(jsonPath("$.parsed.extractedIdentity.last_name").value("TREMBLAY"))
                .andExpect(jsonPath("$.parsed.extractedIdentity.document_code").value("QUEBEC_DRIVER_LICENSE"))
                .andExpect(jsonPath("$.parsed.extractedIdentity.schema_version").value("2024"))
                .andExpect(jsonPath("$.parsed.extractedIdentity.document_type").value("driving_license"));
    }

    @Test
    void ambiguousDateKeepsTheRawReading() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-ambiguous-date")
                        .param("until", "normalize")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.field == 'dateOfBirth')].value").value(hasItem("12/05/1990")))
                .andExpect(jsonPath("$.fields[?(@.field == 'dateOfBirth')].normalizedValue").value(hasItem(nullValue())))
                .andExpect(jsonPath("$.fields[?(@.field == 'dateOfBirth')].validationStatus").value(hasItem("AMBIGUOUS")));
    }

    @Test
    void birthAfterIssueIsAnL3Finding() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-dob-after-issue")
                        .param("until", "normalize")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation[?(@.code == 'DOB_AFTER_ISSUE')].level").value(hasItem("L3")))
                .andExpect(jsonPath("$.decision").value(nullValue()));
    }

    @Test
    void quebecMrzIsNotApplicable() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-driver-license")
                        .param("until", "mrz")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("mrz"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.mrz.format").value("NONE"))
                .andExpect(jsonPath("$.mrz.status").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.mrz.mrzScore").value(nullValue()))
                .andExpect(jsonPath("$.decision").value(nullValue()));
    }

    @Test
    void passportTd3ShowsRecalculatedCheckDigits() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/passport-td3")
                        .param("until", "mrz")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("mrz"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.mrz.format").value("TD3"))
                .andExpect(jsonPath("$.mrz.status").value("VALID"))
                .andExpect(jsonPath("$.mrz.mrzScore").value(1.0))
                .andExpect(jsonPath("$.mrz.lines", hasSize(2)))
                .andExpect(jsonPath("$.mrz.checkDigits[?(@.field == 'documentNumber')].calculated").value(hasItem("6")))
                .andExpect(jsonPath("$.mrz.checkDigits[?(@.field == 'documentNumber')].valid").value(hasItem(true)))
                .andExpect(jsonPath("$.fields[?(@.field == 'lastName')].source").value(hasItem("VISUAL_AND_MRZ")))
                .andExpect(jsonPath("$.decision").value(nullValue()));
    }

    @Test
    void cleanQuebecFixtureIsApproved() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-clean")
                        .param("until", "decide")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("decide"))
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.scores.documentTypeScore").value(0.94))
                .andExpect(jsonPath("$.scores.mrzScore").value(nullValue()))
                .andExpect(jsonPath("$.scores.overallScore", greaterThan(0.90)))
                .andExpect(jsonPath("$.scores.band").value("HIGH_CONFIDENCE"))
                .andExpect(jsonPath("$.decision.issue").value("PASS"))
                .andExpect(jsonPath("$.decision.verificationDecision").value("APPROVED"))
                .andExpect(jsonPath("$.decision.rulesVersion").value("vision-1"))
                .andExpect(jsonPath("$.decision.reasons", hasSize(0)));
    }

    @Test
    void canadaPermanentResidentBackIsApproved() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/canada-pr-back")
                        .param("until", "decide")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsed.classification.code").value("CANADA_PERMANENT_RESIDENT"))
                .andExpect(jsonPath("$.parsed.classification.side").value("BACK"))
                .andExpect(jsonPath("$.schemaVersionId").value(SchemaRegistry.CANADA_PR_BACK_VERSION_ID.toString()))
                .andExpect(jsonPath("$.mrz.format").value("TD1"))
                .andExpect(jsonPath("$.mrz.status").value("VALID"))
                .andExpect(jsonPath("$.fields[?(@.field == 'expirationDate')].normalizedValue").value(hasItem("2030-10-21")))
                .andExpect(jsonPath("$.fields[?(@.field == 'documentNumber')].source").value(hasItem("VISUAL_AND_MRZ")))
                .andExpect(jsonPath("$.decision.issue").value("PASS"))
                .andExpect(jsonPath("$.decision.verificationDecision").value("APPROVED"))
                .andExpect(jsonPath("$.decision.rulesVersion").value("vision-1"));
    }

    @Test
    void quebecBackFixtureIsApproved() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-back")
                        .param("until", "decide")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsed.classification.side").value("BACK"))
                .andExpect(jsonPath("$.schemaVersionId").value(SchemaRegistry.QUEBEC_BACK_VERSION_ID.toString()))
                .andExpect(jsonPath("$.fields[?(@.field == 'barcode')].validationStatus").value(hasItem("VALID")))
                .andExpect(jsonPath("$.decision.issue").value("PASS"))
                .andExpect(jsonPath("$.decision.verificationDecision").value("APPROVED"))
                .andExpect(jsonPath("$.decision.rulesVersion").value("vision-1"));
    }

    @Test
    void expiredQuebecFixtureIsDeclined() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-expired")
                        .param("until", "decide")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.decision.issue").value("REJECT"))
                .andExpect(jsonPath("$.decision.verificationDecision").value("DECLINED"))
                .andExpect(jsonPath("$.decision.rulesVersion").value("vision-1"))
                .andExpect(jsonPath("$.decision.reasons[0]").value("document_expired"));
    }

    @Test
    void unknownCodeIsReviewNotDeclined() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/unknown-code")
                        .param("until", "decide")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.decision.issue").value("REVIEW"))
                .andExpect(jsonPath("$.decision.verificationDecision").value("REVIEW"))
                .andExpect(jsonPath("$.decision.reasons[0]").value("DOCUMENT_UNKNOWN"))
                .andExpect(jsonPath("$.scores.documentTypeScore").value(0.0));
    }

    @Test
    void possibleAlterationIsReviewNotReject() throws Exception {
        mockMvc.perform(post("/v1/document-ia/fixtures/quebec-alteration")
                        .param("until", "decide")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerCalled").value(false))
                .andExpect(jsonPath("$.scores.band").value("HIGH_CONFIDENCE"))
                .andExpect(jsonPath("$.decision.issue").value("REVIEW"))
                .andExpect(jsonPath("$.decision.verificationDecision").value("REVIEW"))
                .andExpect(jsonPath("$.decision.reasons[0]").value("SUSPICIOUS_INDICATOR"))
                .andExpect(jsonPath("$.validation[?(@.code == 'possible_alteration')].level").value(hasItem("L6")))
                .andExpect(jsonPath("$.indicators", hasItem("possible_alteration")))
                .andExpect(jsonPath("$.indicators", not(hasItem("FORGED"))));
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

    private static MockMultipartFile png(String name, BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, out.toByteArray());
    }

    private static BufferedImage sampledChecker(int width, int height, int dark, int light) {
        int step = Math.max(1, (int) Math.round(Math.min(width, height) / 320.0));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int sampleWidth = Math.max(1, width / step);
        int sampleHeight = Math.max(1, height / step);
        for (int gy = 0; gy < sampleHeight; gy++) {
            for (int gx = 0; gx < sampleWidth; gx++) {
                image.setRGB(
                        Math.min(width - 1, gx * step),
                        Math.min(height - 1, gy * step),
                        ((gx + gy) % 2 == 0) ? dark : light);
            }
        }
        return image;
    }
}
