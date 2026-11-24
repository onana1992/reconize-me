package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.adapters.InMemoryFaceCollectionClient;
import com.kyc.entities.ApiKey;
import com.kyc.entities.Integration;
import com.kyc.entities.Organization;
import com.kyc.entities.StripeCustomer;
import com.kyc.entities.Verification;
import com.kyc.enums.EnrollmentStatus;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.enums.VerificationDecision;
import com.kyc.ports.FaceCollectionClient;
import com.kyc.ports.FaceCollectionClient.SearchResult;
import com.kyc.ports.MailPort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.EnrollmentRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.StripeCustomerRepository;
import com.kyc.services.ApiKeyAuthenticator;
import com.kyc.services.CryptoTokens;
import com.kyc.services.EnrollmentService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class EnrollmentFromIdvTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private StripeCustomerRepository stripeCustomers;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EnrollmentRepository enrollments;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private FaceCollectionClient faceCollectionClient;

    @Test
    void liveApprovedWithConditionsCreatesActiveEnrollment() {
        Fixture fixture = seed("user-42", true);
        byte[] reference = ("ref-" + fixture.orgId()).getBytes(StandardCharsets.UTF_8);

        enrollmentService.afterIdvApproved(
                fixture.verification(),
                fixture.identity(),
                List.of("liveness_pass", "face_match_pass"),
                reference);

        var active = enrollments
                .findByOrganizationIdAndExternalIdAndStatus(
                        fixture.orgId(), "user-42", EnrollmentStatus.ACTIVE)
                .orElseThrow();
        assertEquals(fixture.verification().getId(), active.getSourceVerificationId());
        assertTrue(client().containsFace(active.getCollectionId(), active.getFaceId()));

        SearchResult.Ok found = assertInstanceOf(
                SearchResult.Ok.class,
                faceCollectionClient.searchFacesByImage(active.getCollectionId(), reference, 80f));
        assertEquals(1, found.matches().size());
        assertEquals(active.getFaceId(), found.matches().get(0).faceId());
    }

    @Test
    void withoutExternalIdDoesNotIndex() {
        Fixture fixture = seed(null, true);
        byte[] reference = ("no-ext-" + fixture.orgId()).getBytes(StandardCharsets.UTF_8);
        String collectionId = faceCollectionClient.ensureCollection(fixture.orgId());

        enrollmentService.afterIdvApproved(
                fixture.verification(),
                fixture.identity(),
                List.of("liveness_pass", "face_match_pass"),
                reference);

        assertTrue(enrollments.findAll().stream()
                .noneMatch(row -> row.getOrganizationId().equals(fixture.orgId())));
        SearchResult.Ok found = assertInstanceOf(
                SearchResult.Ok.class, faceCollectionClient.searchFacesByImage(collectionId, reference, 80f));
        assertTrue(found.matches().isEmpty());
    }

    @Test
    void withoutEnrollmentOptionDoesNotIndex() {
        Fixture fixture = seed("user-off", false);
        byte[] reference = ("option-off-" + fixture.orgId()).getBytes(StandardCharsets.UTF_8);

        enrollmentService.afterIdvApproved(
                fixture.verification(),
                fixture.identity(),
                List.of("liveness_pass", "face_match_pass"),
                reference);

        assertTrue(enrollments
                .findByOrganizationIdAndExternalIdAndStatus(
                        fixture.orgId(), "user-off", EnrollmentStatus.ACTIVE)
                .isEmpty());
    }

    @Test
    void secondApprovedRetiresPreviousAndDeletesOldFace() {
        Fixture first = seed("user-replace", true);
        byte[] firstRef = ("first-" + first.orgId()).getBytes(StandardCharsets.UTF_8);
        enrollmentService.afterIdvApproved(
                first.verification(),
                first.identity(),
                List.of("liveness_pass", "face_match_pass"),
                firstRef);
        String oldFaceId = enrollments
                .findByOrganizationIdAndExternalIdAndStatus(
                        first.orgId(), "user-replace", EnrollmentStatus.ACTIVE)
                .orElseThrow()
                .getFaceId();

        Verification second = new Verification(
                UUID.randomUUID(),
                first.orgId(),
                first.identity().getId(),
                "user-replace",
                null,
                null,
                null,
                null,
                "hash2",
                Instant.parse("2026-11-22T13:00:00Z"),
                null,
                Instant.parse("2026-11-22T13:00:00Z"));
        second.decide(
                VerificationDecision.APPROVED,
                "[\"liveness_pass\",\"face_match_pass\"]",
                "bio-1",
                null,
                Instant.parse("2026-11-22T13:01:00Z"));
        byte[] secondRef = ("second-" + first.orgId()).getBytes(StandardCharsets.UTF_8);
        enrollmentService.afterIdvApproved(
                second, first.identity(), List.of("liveness_pass", "face_match_pass"), secondRef);

        var active = enrollments
                .findByOrganizationIdAndExternalIdAndStatus(
                        first.orgId(), "user-replace", EnrollmentStatus.ACTIVE)
                .orElseThrow();
        assertEquals(second.getId(), active.getSourceVerificationId());
        assertFalse(oldFaceId.equals(active.getFaceId()));
        assertFalse(client().containsFace(active.getCollectionId(), oldFaceId));
        assertTrue(client().containsFace(active.getCollectionId(), active.getFaceId()));
        assertEquals(
                1,
                enrollments
                        .findByOrganizationIdAndExternalIdOrderByCreatedAtDesc(first.orgId(), "user-replace")
                        .stream()
                        .filter(row -> row.getStatus() == EnrollmentStatus.ACTIVE)
                        .count());
    }

    @Test
    void revokeDeletesFaceAndLookupShowsRevoked() throws Exception {
        Fixture fixture = seed("user-revoke", true);
        byte[] reference = ("revoke-" + fixture.orgId()).getBytes(StandardCharsets.UTF_8);
        enrollmentService.afterIdvApproved(
                fixture.verification(),
                fixture.identity(),
                List.of("liveness_pass", "face_match_pass"),
                reference);
        var active = enrollments
                .findByOrganizationIdAndExternalIdAndStatus(
                        fixture.orgId(), "user-revoke", EnrollmentStatus.ACTIVE)
                .orElseThrow();
        String faceId = active.getFaceId();
        String collectionId = active.getCollectionId();
        String authKey = issueAuthKey(fixture.orgId());

        mockMvc.perform(post("/v1/enrollments/" + active.getId() + "/revoke")
                        .header("Authorization", "Bearer " + authKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("revoked"));

        assertFalse(client().containsFace(collectionId, faceId));
        SearchResult.Ok found = assertInstanceOf(
                SearchResult.Ok.class, faceCollectionClient.searchFacesByImage(collectionId, reference, 80f));
        assertTrue(found.matches().isEmpty());

        mockMvc.perform(get("/v1/enrollments")
                        .header("Authorization", "Bearer " + authKey)
                        .param("external_id", "user-revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("revoked"))
                .andExpect(jsonPath("$.id").value(active.getId().toString()));
    }

    @Test
    void postEnrollmentsRequiresIdv() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "enroll-post@example.com", "Enroll Post");
        credit(owner, 5000);
        MvcResult created = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"authentication\",\"mode\":\"live\",\"name\":\"Auth\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String key = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("key").asText();

        mockMvc.perform(post("/v1/enrollments").header("Authorization", "Bearer " + key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("enrollment_requires_idv"));

        mockMvc.perform(get("/v1/enrollments")
                        .header("Authorization", "Bearer " + key)
                        .param("external_id", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("absent"));
    }

    private Fixture seed(String externalId, boolean enrollmentEnabled) {
        Instant now = Instant.parse("2026-11-22T12:00:00Z");
        UUID orgId = UUID.randomUUID();
        Organization org = new Organization(orgId, "Enroll Org", "enroll-" + orgId, now);
        org.setEnrollmentEnabled(enrollmentEnabled);
        organizations.save(org);
        Integration identity = integrations.save(new Integration(
                UUID.randomUUID(), orgId, ProductCode.IDENTITY, IntegrationMode.LIVE, "Production", now));
        Verification verification = new Verification(
                UUID.randomUUID(),
                orgId,
                identity.getId(),
                externalId,
                null,
                null,
                null,
                null,
                "hash",
                now.plusSeconds(3600),
                null,
                now);
        verification.decide(
                VerificationDecision.APPROVED,
                "[\"liveness_pass\",\"face_match_pass\"]",
                "bio-1",
                null,
                now);
        return new Fixture(orgId, identity, verification);
    }

    private String issueAuthKey(UUID orgId) {
        Instant now = Instant.now();
        Integration auth = integrations.save(new Integration(
                UUID.randomUUID(), orgId, ProductCode.AUTHENTICATION, IntegrationMode.LIVE, "Auth", now));
        String raw = CryptoTokens.randomApiKey(true);
        apiKeys.save(new ApiKey(
                UUID.randomUUID(),
                orgId,
                auth.getId(),
                raw.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(raw),
                now));
        return raw;
    }

    private InMemoryFaceCollectionClient client() {
        return (InMemoryFaceCollectionClient) faceCollectionClient;
    }

    private void credit(Cookie owner, long pack) throws Exception {
        UUID orgId = AccountSupport.organizationId(mockMvc, owner);
        MvcResult checkout = mockMvc.perform(post("/v1/console/billing/checkout")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pack_minor\":" + pack + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        String url = IdvSupport.JSON.readTree(checkout.getResponse().getContentAsString()).get("url").asText();
        String sessionId = url.substring(url.lastIndexOf('/') + 1);
        String customerId = stripeCustomers.findById(orgId).map(StripeCustomer::getStripeCustomerId).orElseThrow();
        String payload = StripeWebhookSupport.checkoutCompleted(
                "evt_" + sessionId, sessionId, customerId, orgId.toString(), UUID.randomUUID().toString(), pack);
        mockMvc.perform(post("/v1/webhooks/stripe")
                        .header("Stripe-Signature", StripeWebhookSupport.signedHeader(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private record Fixture(UUID orgId, Integration identity, Verification verification) {}
}
