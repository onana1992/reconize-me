package com.kyc;

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

@SpringBootTest(properties = "kyc.anti-spoof.lab-enabled=false")
@AutoConfigureMockMvc
@Transactional
class AntiSpoofLabDisabledTest {

    private static final String LIVE = "ky_live_as6off01";

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

    @BeforeEach
    void seed() {
        Instant now = Instant.parse("2026-11-05T12:00:00Z");
        UUID organizationId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        organizations.save(new Organization(organizationId, "Anti-spoof Off", "as-off", now));
        integrations.save(new Integration(
                integrationId, organizationId, ProductCode.IDENTITY, IntegrationMode.LIVE, "LIVE", now));
        apiKeys.save(new ApiKey(
                UUID.randomUUID(),
                organizationId,
                integrationId,
                LIVE.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(LIVE),
                now));
    }

    @Test
    void disabledLabIsNotFound() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "card.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/v1/anti-spoof/analyze").file(file).header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }
}
