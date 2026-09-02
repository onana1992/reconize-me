package com.kyc;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.ApiKey;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Organization;
import com.kyc.ports.HostedTokenStore;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreateVerificationTest {

    private static final String RAW_KEY = "ky_test_localdev1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private VerificationRepository verificationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private HostedTokenStore hostedTokenStore;

    private UUID organizationId;

    @BeforeEach
    void seed() {
        Instant now = Instant.parse("2026-08-26T12:00:00Z");
        organizationId = UUID.fromString("018f0000-0000-7000-8000-000000000001");
        organizationRepository.save(new Organization(organizationId, "Tenant A", "tenant-a", now));
        apiKeyRepository.save(new ApiKey(
                UUID.fromString("018f0000-0000-7000-8000-000000000011"),
                organizationId,
                RAW_KEY.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(RAW_KEY),
                now));
    }

    @Test
    void postWithoutAuthIsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/verifications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void postEmptyBodyCreatesVerification() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.hosted_url").value(startsWith("http://localhost:3001/flow/")))
                .andExpect(jsonPath("$.expires_at").exists())
                .andExpect(jsonPath("$.hosted_token_hash").doesNotExist())
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        UUID verificationId = UUID.fromString(id);
        assertEquals(1, verificationRepository.findByIdAndOrganizationId(verificationId, organizationId).stream().count());

        List<AuditEvent> events = auditEventRepository.findByResourceIdOrderByIdAsc(verificationId);
        assertEquals(List.of("verification.created", "hosted_link.issued"), events.stream().map(AuditEvent::getAction).toList());
        assertEquals("{\"via\":\"api\"}", events.get(0).getPayload());
        org.junit.jupiter.api.Assertions.assertFalse(events.get(1).getPayload().contains("flow/"));
        org.junit.jupiter.api.Assertions.assertTrue(events.get(1).getPayload().contains("expires_at"));
    }

    @Test
    void getReturnsSameDtoAndHostedUrlWhileTokenExists() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-42\",\"applicant\":{\"first_name\":\"Jean\",\"last_name\":\"Martin\",\"email\":\"jean.martin@example.com\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.external_id").value("user-42"))
                .andExpect(jsonPath("$.applicant.first_name").value("Jean"))
                .andReturn();

        String body = created.getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        String hostedUrl = com.jayway.jsonpath.JsonPath.read(body, "$.hosted_url");

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.hosted_url").value(hostedUrl))
                .andExpect(jsonPath("$.external_id").value("user-42"))
                .andExpect(jsonPath("$.applicant.email").value("jean.martin@example.com"))
                .andExpect(jsonPath("$.hosted_token_hash").doesNotExist());

        hostedTokenStore.delete(UUID.fromString(id));

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.hosted_url").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void duplicateExternalIdConflicts() throws Exception {
        String body = "{\"external_id\":\"user-42\"}";
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("external_id_conflict"));
    }

    @Test
    void idempotencyReplaysSameVerification() throws Exception {
        String body = "{\"external_id\":\"idem-1\"}";
        MvcResult first = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .header("Idempotency-Key", "key-aaaa-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .header("Idempotency-Key", "key-aaaa-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        assertEquals(1, verificationRepository.count());
    }

    @Test
    void idempotencyKeyWithDifferentBodyConflicts() throws Exception {
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .header("Idempotency-Key", "key-bbbb-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"idem-a\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .header("Idempotency-Key", "key-bbbb-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"idem-b\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("idempotency_key_conflict"));
    }

    @Test
    void invalidExternalIdIsValidationError() throws Exception {
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"bad id\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }
}
