package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.CreditAccount;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(M5FixtureAwsConfig.class)
class LiveDecisionQcTest {

    private static final String KEY = "ky_live_livedec01";

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
    void liveProducesQcExtracts() throws Exception {
        String orgId = seedLive();
        credits.save(new CreditAccount(
                java.util.UUID.fromString(orgId), "usd", 5000, Instant.parse("2026-09-10T12:00:00Z")));
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        var done = IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
        org.junit.jupiter.api.Assertions.assertEquals("approved", done.path("status").asText());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("approved"))
                .andExpect(jsonPath("$.rules_version").value("m5-1"))
                .andExpect(jsonPath("$.extracted_identity.document_type").value("driving_license"))
                .andExpect(jsonPath("$.extracted_identity.document_country").value("CA"))
                .andExpect(jsonPath("$.extracted_identity.issuing_jurisdiction").value("QC"))
                .andExpect(jsonPath("$.extracted_identity.first_name").value("ANNE-MARIE"));
    }

    private String seedLive() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        java.util.UUID organizationId = java.util.UUID.randomUUID();
        java.util.UUID integrationId = java.util.UUID.randomUUID();
        organizations.save(new com.kyc.entities.Organization(organizationId, "Live QC Co", "live-qc-co", now));
        integrations.save(new com.kyc.entities.Integration(
                integrationId,
                organizationId,
                com.kyc.enums.ProductCode.IDENTITY,
                com.kyc.enums.IntegrationMode.LIVE,
                "Production",
                now));
        apiKeys.save(new com.kyc.entities.ApiKey(
                java.util.UUID.randomUUID(),
                organizationId,
                integrationId,
                KEY.substring(0, com.kyc.services.ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(KEY),
                now));
        return organizationId.toString();
    }
}
