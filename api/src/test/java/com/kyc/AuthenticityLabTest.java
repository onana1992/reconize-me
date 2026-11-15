package com.kyc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class AuthenticityLabTest {

    private static final String LIVE = "ky_live_a0auth01";
    private static final String TEST = "ky_test_a0auth01";

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
        seed(LIVE, IntegrationMode.LIVE, "Auth Lab Live", "auth-lab-live");
        seed(TEST, IntegrationMode.TEST, "Auth Lab Test", "auth-lab-test");
    }

    @Test
    void missingBearerIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(jpeg())
                        .param("documentId", "QUEBEC_DRIVER_LICENSE:2024:FRONT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void templatesIsEmptyAndListedInOpenApi() throws Exception {
        mockMvc.perform(get("/v1/authenticity/templates").header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates", hasSize(0)));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/authenticity/check']").exists())
                .andExpect(jsonPath("$.paths['/v1/authenticity/templates']").exists());
    }

    @Test
    void checkReturnsEmptyEnvelopeWithoutCreatingVerification() throws Exception {
        long before = verifications.count();
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(jpeg())
                        .param("documentId", "QUEBEC_DRIVER_LICENSE:2024:FRONT")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("template-1"))
                .andExpect(jsonPath("$.stoppedAt").value("decide"))
                .andExpect(jsonPath("$.documentId").value("QUEBEC_DRIVER_LICENSE:2024:FRONT"))
                .andExpect(jsonPath("$.verdict").value(nullValue()))
                .andExpect(jsonPath("$.codes", hasSize(0)))
                .andExpect(jsonPath("$.rectify").value(nullValue()))
                .andExpect(jsonPath("$.fixed").value(nullValue()))
                .andExpect(jsonPath("$.positions").value(nullValue()));
        org.junit.jupiter.api.Assertions.assertEquals(before, verifications.count());
    }

    @Test
    void testKeyCanCallLab() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(jpeg())
                        .param("documentId", "QUEBEC_DRIVER_LICENSE:2024:FRONT")
                        .param("until", "rectify")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("template-1"))
                .andExpect(jsonPath("$.stoppedAt").value("rectify"));
    }

    @Test
    void blankDocumentIdIsRejected() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(jpeg())
                        .param("documentId", "  ")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("template_not_found"));
    }

    @Test
    void unknownUntilIsRejected() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(jpeg())
                        .param("documentId", "QUEBEC_DRIVER_LICENSE:2024:FRONT")
                        .param("until", "content")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"))
                .andExpect(jsonPath("$.error.details[0].field").value("until"));
    }

    private void seed(String rawKey, IntegrationMode mode, String orgName, String slug) {
        Instant now = Instant.parse("2026-11-15T12:00:00Z");
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

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "doc.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
    }
}
