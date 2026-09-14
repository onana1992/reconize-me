package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.HostedTokenStore;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HostedFlowTest {

    private static final String KEY = "ky_test_flow0001";

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
    private HostedTokenStore hostedTokens;

    @Test
    void openConsentConflictAndExpired() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Flow Co", "flow-co");
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();

        mockMvc.perform(get("/v1/flow/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("pending_consent"))
                .andExpect(jsonPath("$.next").value("consent"))
                .andExpect(jsonPath("$.consent_text_version").value("consent-v1"));

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.status").value("pending_consent"));

        mockMvc.perform(post("/v1/flow/" + token + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending_applicant"))
                .andExpect(jsonPath("$.next").value("capture_document"));

        mockMvc.perform(post("/v1/flow/" + token + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("consent_already_recorded"));

        mockMvc.perform(get("/v1/flow/not-a-real-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));

        hostedTokens.revokeByVerificationId(UUID.fromString(id));
        mockMvc.perform(get("/v1/flow/" + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("hosted_link_expired"));
    }

    @Test
    void declinedConsentHasNoMediaAndNextDone() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Flow Co", "flow-co");
        String token = IdvSupport.token(IdvSupport.create(mockMvc, KEY, "{}"));
        mockMvc.perform(get("/v1/flow/" + token)).andExpect(status().isOk());
        mockMvc.perform(post("/v1/flow/" + token + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"declined\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.next").value("done"));
        mockMvc.perform(post("/v1/flow/" + token + "/document/uploads"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("invalid_status"));
    }

    @Test
    void consentAcceptsDecisionQueryWhenBodyMissing() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Flow Co", "flow-co");
        String token = IdvSupport.token(IdvSupport.create(mockMvc, KEY, "{}"));
        mockMvc.perform(get("/v1/flow/" + token)).andExpect(status().isOk());
        mockMvc.perform(post("/v1/flow/" + token + "/consent").param("decision", "accepted"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending_applicant"))
                .andExpect(jsonPath("$.next").value("capture_document"));
    }
}
