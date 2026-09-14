package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.MailPort;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewTest {

    private static final String KEY = "ky_test_review01";

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
    void reviewThenApproveAndRejectOutOfWindow() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Review Co", "review-co");
        var created = IdvSupport.create(mockMvc, KEY, "{\"metadata\":{\"sandbox_scenario\":\"review\"}}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.status").value("review"));

        var owner = AccountSupport.signupVerified(mockMvc, mailPort, "review-owner@example.com", "Review Console");
        mockMvc.perform(post("/v1/console/verifications/" + id + "/review")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/v1/verifications/" + id + "/review")
                        .header("Authorization", "Bearer " + KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.decision").value("approved"));

        mockMvc.perform(post("/v1/verifications/" + id + "/review")
                        .header("Authorization", "Bearer " + KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"declined\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("invalid_status"));
    }

    @Test
    void developerCannotReviewOnConsole() throws Exception {
        var owner = AccountSupport.signupVerified(mockMvc, mailPort, "review-dev-owner@example.com", "Dev Review");
        AccountSupport.invite(mockMvc, owner, "review-dev@example.com", "developer");
        var developer = AccountSupport.acceptInvite(mockMvc, mailPort, "review-dev@example.com");
        mockMvc.perform(post("/v1/console/verifications")
                        .cookie(developer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void consoleOwnerReviewsOwnSessionAndMediaIsIsolated() throws Exception {
        var owner = AccountSupport.signupVerified(mockMvc, mailPort, "review-media@example.com", "Media Review");
        var created = IdvSupport.createConsole(
                mockMvc, owner, "{\"metadata\":{\"sandbox_scenario\":\"review\"}}");
        String token = IdvSupport.token(created);
        String id = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());

        mockMvc.perform(get("/v1/console/verifications/" + id).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("review"));

        mockMvc.perform(get("/v1/console/verifications/" + id + "/media/document").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.expires_at").exists());

        var other = AccountSupport.signupVerified(mockMvc, mailPort, "review-other@example.com", "Other Review");
        mockMvc.perform(get("/v1/console/verifications/" + id + "/media/document").cookie(other))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/v1/console/verifications/" + id + "/review")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"declined\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"));
    }
}
