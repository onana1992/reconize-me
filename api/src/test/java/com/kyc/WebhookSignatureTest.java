package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.services.WebhookSigner;
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
class WebhookSignatureTest {

    private static final String KEY = "ky_test_whsign01";

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
    void upsertReturnsSecretOnceAndSignatureFormatIsStable() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Wh Co", "wh-co");

        var created = IdvSupport.JSON.readTree(mockMvc.perform(put("/v1/webhooks")
                        .header("Authorization", "Bearer " + KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://localhost:9999/hook\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").exists())
                .andExpect(jsonPath("$.secret_prefix").exists())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String secret = created.path("secret").asText();
        mockMvc.perform(get("/v1/webhooks").header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist());

        String body = "{\"hello\":\"world\"}";
        String signature = WebhookSigner.sign(secret, 1710000000L, body);
        org.junit.jupiter.api.Assertions.assertTrue(signature.startsWith("t=1710000000,v1="));
        org.junit.jupiter.api.Assertions.assertEquals(64, signature.substring("t=1710000000,v1=".length()).length());

        mockMvc.perform(post("/v1/webhooks")
                        .header("Authorization", "Bearer " + KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://localhost:9999/hook2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }
}
