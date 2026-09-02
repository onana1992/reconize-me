package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.ApiKey;
import com.kyc.entities.Organization;
import com.kyc.entities.Verification;
import com.kyc.entities.VerificationStatus;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiKeyAuthTest {

    private static final String RAW_KEY = "ky_test_localdev1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private VerificationRepository verificationRepository;

    private UUID organizationId;
    private UUID verificationId;

    @BeforeEach
    void seed() {
        Instant now = Instant.parse("2026-08-26T12:00:00Z");
        organizationId = UUID.fromString("018f0000-0000-7000-8000-000000000001");
        UUID otherOrganizationId = UUID.fromString("018f0000-0000-7000-8000-000000000002");
        verificationId = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");

        organizationRepository.save(new Organization(organizationId, "Tenant A", "tenant-a", now));
        organizationRepository.save(new Organization(otherOrganizationId, "Tenant B", "tenant-b", now));

        apiKeyRepository.save(new ApiKey(
                UUID.fromString("018f0000-0000-7000-8000-000000000011"),
                organizationId,
                RAW_KEY.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(RAW_KEY),
                now));

        verificationRepository.save(new Verification(
                verificationId,
                organizationId,
                "ext-1",
                VerificationStatus.CREATED,
                "a".repeat(64),
                now.plusSeconds(3600),
                now));
        verificationRepository.save(new Verification(
                UUID.fromString("018f0000-0000-7000-8000-0000000000bb"),
                otherOrganizationId,
                "ext-2",
                VerificationStatus.CREATED,
                "b".repeat(64),
                now.plusSeconds(3600),
                now));
    }

    @Test
    void missingBearerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/verifications/" + verificationId).header("X-Request-Id", "req_missing_key"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "req_missing_key"))
                .andExpect(jsonPath("$.error.code").value("unauthorized"))
                .andExpect(jsonPath("$.error.message").value("Invalid or missing API key"))
                .andExpect(jsonPath("$.error.request_id").value("req_missing_key"));
    }

    @Test
    void invalidKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/verifications/" + verificationId).header("Authorization", "Bearer ky_test_unknownxx"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", Matchers.startsWith("req_")))
                .andExpect(jsonPath("$.error.code").value("unauthorized"))
                .andExpect(jsonPath("$.error.request_id").exists());
    }

    @Test
    void validKeyReadsOwnVerification() throws Exception {
        mockMvc.perform(get("/v1/verifications/" + verificationId)
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .header("X-Request-Id", "req_ok_read"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req_ok_read"))
                .andExpect(jsonPath("$.id").value(verificationId.toString()))
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.hosted_url").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.hosted_token_hash").doesNotExist());
    }

    @Test
    void unknownAndCrossTenantNotFoundAreIdentical() throws Exception {
        UUID unknownId = UUID.fromString("018f0000-0000-7000-8000-0000000000ff");
        String unknown = mockMvc.perform(get("/v1/verifications/" + unknownId)
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .header("X-Request-Id", "req_not_found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "req_not_found"))
                .andExpect(jsonPath("$.error.code").value("not_found"))
                .andExpect(jsonPath("$.error.message").value("Verification not found"))
                .andExpect(jsonPath("$.error.request_id").value("req_not_found"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String crossTenant = mockMvc.perform(get("/v1/verifications/018f0000-0000-7000-8000-0000000000bb")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .header("X-Request-Id", "req_not_found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "req_not_found"))
                .andExpect(jsonPath("$.error.code").value("not_found"))
                .andExpect(jsonPath("$.error.message").value("Verification not found"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(unknown, crossTenant);
    }
}
