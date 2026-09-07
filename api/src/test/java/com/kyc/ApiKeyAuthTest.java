package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.ApiKey;
import com.kyc.entities.Organization;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.OrganizationRepository;
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

    @BeforeEach
    void seed() {
        Instant now = Instant.parse("2026-08-26T12:00:00Z");
        UUID organizationId = UUID.fromString("018f0000-0000-7000-8000-000000000001");
        organizationRepository.save(new Organization(organizationId, "Tenant A", "tenant-a", now));
        apiKeyRepository.save(new ApiKey(
                UUID.fromString("018f0000-0000-7000-8000-000000000011"),
                organizationId,
                RAW_KEY.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(RAW_KEY),
                now));
    }

    @Test
    void missingBearerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/verifications").header("X-Request-Id", "req_missing_key"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "req_missing_key"))
                .andExpect(jsonPath("$.error.code").value("unauthorized"))
                .andExpect(jsonPath("$.error.request_id").value("req_missing_key"));
    }

    @Test
    void invalidKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/verifications").header("Authorization", "Bearer ky_test_unknownxx"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", Matchers.startsWith("req_")))
                .andExpect(jsonPath("$.error.code").value("unauthorized"))
                .andExpect(jsonPath("$.error.request_id").exists());
    }

    @Test
    void validKeyIsAuthenticatedOnReservedProductPath() throws Exception {
        mockMvc.perform(get("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .header("X-Request-Id", "req_ok_read"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "req_ok_read"))
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }
}
