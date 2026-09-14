package com.kyc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kyc.ports.MailPort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
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
class IsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Test
    void otherOrganizationSeesNotFoundAndFlowHasNoPii() throws Exception {
        String keyA = IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, "ky_test_aaa00001", "Iso A", "iso-a-idv");
        String keyB = IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, "ky_test_bbb00001", "Iso B", "iso-b-idv");
        JsonNode created = IdvSupport.create(
                mockMvc,
                keyA,
                """
                {"applicant":{"email":"secret.applicant@example.com","first_name":"Marie"},"metadata":{"note":"internal"}}
                """);
        String idA = created.path("id").asText();
        String token = IdvSupport.token(created);

        mockMvc.perform(get("/v1/verifications/" + idA).header("Authorization", "Bearer " + keyB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));

        mockMvc.perform(get("/v1/verifications").header("Authorization", "Bearer " + keyB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        var cookieB = AccountSupport.signupVerified(mockMvc, mailPort, "iso-b-console@example.com", "Iso B Console");
        mockMvc.perform(get("/v1/console/verifications/" + idA).cookie(cookieB))
                .andExpect(status().isNotFound());

        var flow = mockMvc.perform(get("/v1/flow/" + token))
                .andExpect(status().isOk())
                .andReturn();
        String json = flow.getResponse().getContentAsString();
        assertFalse(json.contains("secret.applicant@example.com"));
        assertFalse(json.contains("Marie"));
        assertFalse(json.contains("Iso A"));
        assertFalse(json.contains("organization_id"));
        JsonNode node = IdvSupport.JSON.readTree(json);
        assertNotEquals(idA, "");
        org.junit.jupiter.api.Assertions.assertEquals(idA, node.path("verification_id").asText());
        org.junit.jupiter.api.Assertions.assertFalse(node.has("applicant"));
        org.junit.jupiter.api.Assertions.assertFalse(node.has("metadata"));
    }
}
