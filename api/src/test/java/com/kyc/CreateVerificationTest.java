package com.kyc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.AuditEvent;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import java.util.List;
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
class CreateVerificationTest {

    private static final String KEY = "ky_test_create01";

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
    private AuditEventRepository auditEvents;

    @Test
    void createReturnsHostedLinkAndReplaysIdempotency() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Create Co", "create-co");
        String body = """
                {"external_id":"ext-1","applicant":{"email":"marie@example.com"},"metadata":{"sandbox_scenario":"approved"}}
                """;
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY)
                        .header("Idempotency-Key", "idem-create-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.hosted_url").value(startsWith("http://localhost:3001/flow/")))
                .andExpect(jsonPath("$.applicant.email").value("marie@example.com"));

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY)
                        .header("Idempotency-Key", "idem-create-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosted_url").exists());

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY)
                        .header("Idempotency-Key", "idem-create-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"other\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("idempotency_key_conflict"));

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"ext-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("external_id_conflict"));

        List<AuditEvent> events = auditEvents.findAll();
        org.junit.jupiter.api.Assertions.assertTrue(
                events.stream().anyMatch(event -> "verification.created".equals(event.getAction())));
        org.junit.jupiter.api.Assertions.assertTrue(
                events.stream().anyMatch(event -> "hosted_link.issued".equals(event.getAction())));
    }

    @Test
    void responseDoesNotLeakTokenHash() throws Exception {
        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "Create Co", "create-co");
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hosted_token_hash").doesNotExist())
                .andExpect(header().string("Content-Type", containsString("application/json")));
        mockMvc.perform(get("/v1/verifications").header("Authorization", "Bearer " + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].hosted_url").value(not("")));
    }
}
