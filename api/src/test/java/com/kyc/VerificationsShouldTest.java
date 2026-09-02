package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.ApiKey;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Organization;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
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
class VerificationsShouldTest {

    private static final String RAW_KEY = "ky_test_should01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private UUID organizationId;

    @BeforeEach
    void seed() {
        Instant now = Instant.parse("2026-08-26T12:00:00Z");
        organizationId = UUID.fromString("018f0000-0000-7000-8000-0000000000c1");
        organizationRepository.save(new Organization(organizationId, "Should Org", "should-org", now));
        apiKeyRepository.save(new ApiKey(
                UUID.fromString("018f0000-0000-7000-8000-0000000000c2"),
                organizationId,
                RAW_KEY.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(RAW_KEY),
                now));
    }

    @Test
    void metadataRoundTrip() throws Exception {
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metadata\":{\"source\":\"onboarding\",\"ref\":{\"app\":\"web\"}}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata.source").value("onboarding"))
                .andExpect(jsonPath("$.metadata.ref.app").value("web"));
    }

    @Test
    void emptyBodyReturnsEmptyMetadata() throws Exception {
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata").isMap());
    }

    @Test
    void metadataTooDeepIsValidationError() throws Exception {
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metadata\":{\"a\":{\"b\":{\"c\":1}}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void metadataTooLargeIsValidationError() throws Exception {
        String padding = "x".repeat(4100);
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metadata\":{\"blob\":\"" + padding + "\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void listIsPaginatedNewestFirst() throws Exception {
        String first = create("{\"external_id\":\"p1\"}");
        String second = create("{\"external_id\":\"p2\"}");
        String third = create("{\"external_id\":\"p3\"}");

        MvcResult page1 = mockMvc.perform(get("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.next_cursor").isString())
                .andReturn();
        String page1Body = page1.getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(page1Body, "$.next_cursor");
        java.util.List<String> page1Ids = com.jayway.jsonpath.JsonPath.read(page1Body, "$.data[*].id");
        java.util.List<String> page1Created = com.jayway.jsonpath.JsonPath.read(page1Body, "$.data[*].created_at");
        Assertions.assertFalse(page1Created.get(0).compareTo(page1Created.get(1)) < 0);

        MvcResult page2 = mockMvc.perform(get("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.next_cursor").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        java.util.List<String> page2Ids =
                com.jayway.jsonpath.JsonPath.read(page2.getResponse().getContentAsString(), "$.data[*].id");
        java.util.Set<String> all = new java.util.HashSet<>();
        all.addAll(page1Ids);
        all.addAll(page2Ids);
        Assertions.assertEquals(java.util.Set.of(first, second, third), all);
        Assertions.assertTrue(java.util.Collections.disjoint(page1Ids, page2Ids));
    }

    @Test
    void listFiltersByStatusAndExternalId() throws Exception {
        String createdId = create("{\"external_id\":\"keep-me\"}");
        create("{\"external_id\":\"other\"}");
        mockMvc.perform(post("/v1/verifications/" + createdId + "/cancel")
                        .header("Authorization", "Bearer " + RAW_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .param("status", "cancelled")
                        .param("external_id", "keep-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(createdId))
                .andExpect(jsonPath("$.data[0].status").value("cancelled"));
    }

    @Test
    void cancelCreatedSetsCancelledAndDropsHostedUrl() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json(created, "$.id");
        String hostedUrl = json(created, "$.hosted_url");
        String token = hostedUrl.substring(hostedUrl.lastIndexOf('/') + 1);

        mockMvc.perform(post("/v1/verifications/" + id + "/cancel").header("Authorization", "Bearer " + RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.hosted_url").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/v1/verifications/" + id).header("Authorization", "Bearer " + RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.hosted_url").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/v1/flow/" + token)).andExpect(status().isGone());

        Assertions.assertTrue(auditEventRepository.findByResourceIdOrderByIdAsc(UUID.fromString(id)).stream()
                .map(AuditEvent::getAction)
                .anyMatch("verification.cancelled"::equals));
    }

    @Test
    void cancelPendingConsentIsAllowed() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json(created, "$.id");
        String hostedUrl = json(created, "$.hosted_url");
        String token = hostedUrl.substring(hostedUrl.lastIndexOf('/') + 1);
        mockMvc.perform(get("/v1/flow/" + token)).andExpect(status().isOk());

        mockMvc.perform(post("/v1/verifications/" + id + "/cancel").header("Authorization", "Bearer " + RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void cancelAfterConsentConflicts() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json(created, "$.id");
        String hostedUrl = json(created, "$.hosted_url");
        String token = hostedUrl.substring(hostedUrl.lastIndexOf('/') + 1);
        mockMvc.perform(post("/v1/flow/" + token + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/verifications/" + id + "/cancel").header("Authorization", "Bearer " + RAW_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("invalid_status"));
    }

    private String create(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result, "$.id");
    }

    private static String json(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
