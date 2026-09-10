package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.repositories.ApiKeyRepository;
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
class SelfieAndStubDecisionTest {

    private static final String KEY = "ky_test_selfie01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Test
    void selfieThenApprovedWithReasons() throws Exception {
        IdvSupport.seedBearer(organizations, apiKeys, passwordEncoder, KEY, "Selfie Co", "selfie-co");
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
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.decision").value("approved"))
                .andExpect(jsonPath("$.decision_reasons[0]").exists())
                .andExpect(jsonPath("$.extracted_identity.first_name").value("Marie"))
                .andExpect(jsonPath("$.signals[0].code").exists());
    }
}
