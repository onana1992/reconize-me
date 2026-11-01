package com.kyc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "kyc.idempotency-ttl-hours=0")
@AutoConfigureMockMvc
@Transactional
class IdempotencyTtlTest {

    private static final String KEY = "ky_test_idemttl01";

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

    @Test
    void expiredKeyAcceptsADifferentBody() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Ttl Co", "ttl-co");
        MvcResult first = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY)
                        .header("Idempotency-Key", "idem-ttl-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"first-ref\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY)
                        .header("Idempotency-Key", "idem-ttl-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"second-ref\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn();

        String firstId = IdvSupport.JSON.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String secondId = IdvSupport.JSON.readTree(second.getResponse().getContentAsString()).get("id").asText();
        assertNotEquals(firstId, secondId);
    }
}
