package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.ApiKey;
import com.kyc.entities.Organization;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import java.time.Instant;
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
class IsolationTest {

    private static final String KEY_A = "ky_test_orgAxxxx";
    private static final String KEY_B = "ky_test_orgBxxxx";

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

    private UUID orgA;
    private UUID orgB;

    @BeforeEach
    void seed() {
        Instant now = Instant.parse("2026-08-26T12:00:00Z");
        orgA = UUID.fromString("018f0000-0000-7000-8000-0000000000a1");
        orgB = UUID.fromString("018f0000-0000-7000-8000-0000000000b1");
        organizationRepository.save(new Organization(orgA, "Tenant A", "iso-tenant-a", now));
        organizationRepository.save(new Organization(orgB, "Tenant B", "iso-tenant-b", now));
        apiKeyRepository.save(new ApiKey(
                UUID.fromString("018f0000-0000-7000-8000-0000000000a2"),
                orgA,
                KEY_A.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(KEY_A),
                now));
        apiKeyRepository.save(new ApiKey(
                UUID.fromString("018f0000-0000-7000-8000-0000000000b2"),
                orgB,
                KEY_B.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(KEY_B),
                now));
    }

    @Test
    void tenantAReadsOwnVerification() throws Exception {
        Created created = create(KEY_A, "{\"external_id\":\"a-1\"}");
        mockMvc.perform(get("/v1/verifications/" + created.id()).header("Authorization", "Bearer " + KEY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id()))
                .andExpect(jsonPath("$.external_id").value("a-1"));
    }

    @Test
    void tenantBGetsNotFoundOnTenantAId() throws Exception {
        Created created = create(KEY_A, "{\"external_id\":\"a-secret\"}");
        mockMvc.perform(get("/v1/verifications/" + created.id()).header("Authorization", "Bearer " + KEY_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"))
                .andExpect(jsonPath("$.error.message").value("Verification not found"));
    }

    @Test
    void sameIdempotencyKeyDoesNotCreateDuplicate() throws Exception {
        String body = "{\"external_id\":\"idem-iso\"}";
        MvcResult first = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY_A)
                        .header("Idempotency-Key", "iso-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json(first, "$.id");

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY_A)
                        .header("Idempotency-Key", "iso-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        assertEquals(1, verificationRepository.count());
    }

    @Test
    void flowTokenDoesNotLeakOtherVerification() throws Exception {
        Created a = create(KEY_A, "{\"external_id\":\"a-flow\",\"applicant\":{\"email\":\"secret-a@example.com\"}}");
        Created b = create(KEY_B, "{\"external_id\":\"b-flow\",\"applicant\":{\"email\":\"secret-b@example.com\"}}");
        assertNotEquals(a.id(), b.id());

        String bodyA = mockMvc.perform(get("/v1/flow/" + a.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification_id").value(a.id()))
                .andExpect(jsonPath("$.applicant").doesNotExist())
                .andExpect(jsonPath("$.organization_id").doesNotExist())
                .andExpect(jsonPath("$.external_id").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(bodyA.contains("secret-a@example.com"));
        org.junit.jupiter.api.Assertions.assertFalse(bodyA.contains("secret-b@example.com"));
        org.junit.jupiter.api.Assertions.assertFalse(bodyA.contains(b.id()));
        org.junit.jupiter.api.Assertions.assertFalse(bodyA.contains(orgA.toString()));
        org.junit.jupiter.api.Assertions.assertFalse(bodyA.contains(orgB.toString()));

        mockMvc.perform(post("/v1/flow/" + a.token() + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/verifications/" + b.id()).header("Authorization", "Bearer " + KEY_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/verifications/" + b.id()).header("Authorization", "Bearer " + KEY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.applicant.email").value("secret-b@example.com"));
    }

    @Test
    void sameExternalIdIsAllowedForOtherOrganization() throws Exception {
        create(KEY_A, "{\"external_id\":\"shared-ext\"}");
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + KEY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"shared-ext\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.external_id").value("shared-ext"));
        assertEquals(2, verificationRepository.count());
    }

    @Test
    void listDoesNotIncludeOtherTenant() throws Exception {
        Created a = create(KEY_A, "{\"external_id\":\"list-a\"}");
        Created b = create(KEY_B, "{\"external_id\":\"list-b\"}");
        mockMvc.perform(get("/v1/verifications").header("Authorization", "Bearer " + KEY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(a.id()))
                .andExpect(jsonPath("$.data[0].id").value(org.hamcrest.Matchers.not(b.id())));
        mockMvc.perform(get("/v1/verifications").header("Authorization", "Bearer " + KEY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(b.id()));
    }

    @Test
    void cancelByOtherTenantIsNotFound() throws Exception {
        Created created = create(KEY_A, "{\"external_id\":\"cancel-a\"}");
        mockMvc.perform(post("/v1/verifications/" + created.id() + "/cancel")
                        .header("Authorization", "Bearer " + KEY_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"))
                .andExpect(jsonPath("$.error.message").value("Verification not found"));
        mockMvc.perform(get("/v1/verifications/" + created.id()).header("Authorization", "Bearer " + KEY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("created"));
    }

    private Created create(String apiKey, String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String hostedUrl = com.jayway.jsonpath.JsonPath.read(body, "$.hosted_url");
        return new Created(
                com.jayway.jsonpath.JsonPath.read(body, "$.id"),
                hostedUrl.substring(hostedUrl.lastIndexOf('/') + 1));
    }

    private static String json(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private record Created(String id, String token) {}
}
