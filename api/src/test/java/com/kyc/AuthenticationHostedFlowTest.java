package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.adapters.InMemoryFaceCollectionClient;
import com.kyc.entities.ApiKey;
import com.kyc.entities.CreditAccount;
import com.kyc.entities.Enrollment;
import com.kyc.entities.Integration;
import com.kyc.entities.Organization;
import com.kyc.enums.EnrollmentSource;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.LedgerResourceType;
import com.kyc.enums.ProductCode;
import com.kyc.ports.FaceCollectionClient;
import com.kyc.ports.FaceLivenessClient;
import com.kyc.ports.LivenessCredentialPort;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuthenticationMediaRepository;
import com.kyc.repositories.AuthenticationRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.CreditLedgerEntryRepository;
import com.kyc.repositories.EnrollmentRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.WebhookDeliveryRepository;
import com.kyc.services.ApiKeyAuthenticator;
import com.kyc.services.CryptoTokens;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "kyc.authenticity.lab-enabled=false")
@AutoConfigureMockMvc
@Transactional
@Import(AuthenticationHostedFlowTest.AuthFlowFakes.class)
class AuthenticationHostedFlowTest {

    static final AtomicInteger CREATES = new AtomicInteger();
    static final AtomicReference<FaceLivenessClient.LivenessResult> NEXT_RESULT = new AtomicReference<>();

    static final byte[] FACE_A = "auth-face-a-reference-bytes".getBytes(StandardCharsets.UTF_8);
    static final byte[] FACE_B = "auth-face-b-reference-bytes".getBytes(StandardCharsets.UTF_8);

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
    private CreditAccountRepository credits;

    @Autowired
    private CreditLedgerEntryRepository ledgerEntries;

    @Autowired
    private EnrollmentRepository enrollments;

    @Autowired
    private AuthenticationRepository authentications;

    @Autowired
    private AuthenticationMediaRepository media;

    @Autowired
    private ObjectStoragePort objectStorage;

    @Autowired
    private WebhookDeliveryRepository webhooks;

    @Autowired
    private InMemoryFaceCollectionClient faceCollection;

    @BeforeEach
    void resetFakes() {
        CREATES.set(0);
        NEXT_RESULT.set(new FaceLivenessClient.LivenessResult(0.95, "SUCCEEDED", FACE_A));
        faceCollection.resetSearchCalls();
    }

    @Test
    void approvedWhenSameFaceAboveThreshold() throws Exception {
        Fixture fixture = seedEnrolled(FACE_A);
        String token = openSession(fixture);

        mockMvc.perform(get("/v1/flow/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("authentication"))
                .andExpect(jsonPath("$.consent_text_version").value("auth-consent-1"))
                .andExpect(jsonPath("$.next").value("consent"));

        acceptConsent(token);
        mockMvc.perform(get("/v1/flow/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("selfie"))
                .andExpect(jsonPath("$.next").value("liveness"));

        String sessionId = openLiveness(token);
        mockMvc.perform(post("/v1/flow/" + token + "/liveness/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.next").value("done"))
                .andExpect(jsonPath("$.reasons[0]").value("liveness_pass"))
                .andExpect(jsonPath("$.reasons[1]").value("face_match_pass"))
                .andExpect(jsonPath("$.match_score").value(Double.valueOf("0.99")));

        mockMvc.perform(get("/v1/flow/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.next").value("done"));

        mockMvc.perform(get("/v1/authentications/" + fixture.authenticationId())
                        .header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.match_score").value(Double.valueOf("0.99")));

        mockMvc.perform(get("/v1/authentications/" + fixture.authenticationId() + "/media/selfie")
                        .header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isNotFound());

        assertEquals(1, faceCollection.searchCalls());
        assertEquals(0, webhooks.count());
        assertEquals(0, media.countByAuthenticationId(fixture.authenticationId()));
        assertFalse(objectStorage.exists(selfieKey(fixture)));
        assertEquals(4960, credits.findById(fixture.orgId()).orElseThrow().getBalanceMinor());
        assertTrue(ledgerEntries.existsByResourceTypeAndResourceId(
                LedgerResourceType.AUTHENTICATION, fixture.authenticationId()));
    }

    @Test
    void otherFaceIdIsFaceMatchFail() throws Exception {
        Fixture fixture = seedEnrolled(FACE_A);
        faceCollection.ensureCollection(fixture.orgId());
        faceCollection.indexFaces("rm-auth-" + fixture.orgId(), FACE_B, UUID.randomUUID().toString());
        NEXT_RESULT.set(new FaceLivenessClient.LivenessResult(0.96, "SUCCEEDED", FACE_B));
        String token = openSession(fixture);
        acceptConsent(token);
        String sessionId = openLiveness(token);

        mockMvc.perform(post("/v1/flow/" + token + "/liveness/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.reasons[0]").value("face_match_fail"));

        mockMvc.perform(get("/v1/authentications/" + fixture.authenticationId())
                        .header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.reasons[0]").value("face_match_fail"));
        assertEquals(1, faceCollection.searchCalls());
        assertEquals(0, webhooks.count());
    }

    @Test
    void belowLivenessThresholdRecapturesWithoutSearch() throws Exception {
        Fixture fixture = seedEnrolled(FACE_A);
        NEXT_RESULT.set(new FaceLivenessClient.LivenessResult(0.79, "SUCCEEDED", FACE_A));
        String token = openSession(fixture);
        acceptConsent(token);
        String sessionId = openLiveness(token);

        mockMvc.perform(post("/v1/flow/" + token + "/liveness/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("recapture_requested"))
                .andExpect(jsonPath("$.next").value("liveness"))
                .andExpect(jsonPath("$.reason").value("liveness_fail"));

        assertEquals(0, faceCollection.searchCalls());
        assertEquals(1, media.countByAuthenticationId(fixture.authenticationId()));
        assertTrue(objectStorage.exists(selfieKey(fixture)));
        mockMvc.perform(get("/v1/authentications/" + fixture.authenticationId())
                        .header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("recapture_requested"));
    }

    @Test
    void revokedEnrollmentDeclinesWithoutSearch() throws Exception {
        Fixture fixture = seedEnrolled(FACE_A);
        String token = openSession(fixture);
        acceptConsent(token);
        Enrollment enrollment = enrollments.findById(fixture.enrollmentId()).orElseThrow();
        enrollment.revoke(Instant.now());
        enrollments.save(enrollment);
        String sessionId = openLiveness(token);

        mockMvc.perform(post("/v1/flow/" + token + "/liveness/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.reasons[0]").value("enrollment_inactive"));

        assertEquals(0, faceCollection.searchCalls());
    }

    @Test
    void consentRefusedHasNoMediaNoSearch() throws Exception {
        Fixture fixture = seedEnrolled(FACE_A);
        String token = openSession(fixture);

        mockMvc.perform(post("/v1/flow/" + token + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"declined\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.next").value("done"));

        mockMvc.perform(get("/v1/flow/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.next").value("done"))
                .andExpect(jsonPath("$.reason").value("consent_refused"));

        mockMvc.perform(get("/v1/authentications/" + fixture.authenticationId())
                        .header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.reasons[0]").value("consent_refused"));

        assertEquals(0, faceCollection.searchCalls());
        assertEquals(0, media.countByAuthenticationId(fixture.authenticationId()));
        assertEquals(0, webhooks.count());
        assertEquals(5000, credits.findById(fixture.orgId()).orElseThrow().getBalanceMinor());
        assertEquals(false, ledgerEntries.existsByResourceTypeAndResourceId(
                LedgerResourceType.AUTHENTICATION, fixture.authenticationId()));
    }

    @Test
    void providerSearchFailureIsFailed() throws Exception {
        Fixture fixture = seedEnrolled(FACE_A);
        faceCollection.failNextSearch("provider_timeout");
        String token = openSession(fixture);
        acceptConsent(token);
        String sessionId = openLiveness(token);

        mockMvc.perform(post("/v1/flow/" + token + "/liveness/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.failure_reason").value("provider_timeout"));

        mockMvc.perform(get("/v1/authentications/" + fixture.authenticationId())
                        .header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    @Test
    void authTokenRejectsDocumentUpload() throws Exception {
        Fixture fixture = seedEnrolled(FACE_A);
        String token = openSession(fixture);
        acceptConsent(token);
        mockMvc.perform(post("/v1/flow/" + token + "/document/uploads"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("invalid_status"));
    }

    private String openSession(Fixture fixture) throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-flow-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        var body = IdvSupport.JSON.readTree(created.getResponse().getContentAsString());
        fixture.authenticationId = UUID.fromString(body.get("id").asText());
        String hostedUrl = body.get("hosted_url").asText();
        return hostedUrl.substring(hostedUrl.lastIndexOf('/') + 1);
    }

    private static String selfieKey(Fixture fixture) {
        return "kyc/org/" + fixture.orgId() + "/authentications/" + fixture.authenticationId() + "/selfie/1";
    }

    private void acceptConsent(String token) throws Exception {
        mockMvc.perform(post("/v1/flow/" + token + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isCreated());
    }

    private String openLiveness(String token) throws Exception {
        return IdvSupport.JSON
                .readTree(mockMvc.perform(post("/v1/flow/" + token + "/liveness/session"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("session_id")
                .asText();
    }

    private Fixture seedEnrolled(byte[] referenceImage) {
        Instant now = Instant.parse("2026-11-22T18:00:00Z");
        UUID orgId = UUID.randomUUID();
        organizations.save(new Organization(orgId, "Auth Flow", "auth-flow-" + orgId, now));
        credits.save(new CreditAccount(orgId, "usd", 5000, now));
        Integration auth = integrations.save(new Integration(
                UUID.randomUUID(), orgId, ProductCode.AUTHENTICATION, IntegrationMode.LIVE, "Auth", now));
        String authKey = issueKey(orgId, auth.getId());
        String collectionId = faceCollection.ensureCollection(orgId);
        FaceCollectionClient.IndexResult indexed =
                faceCollection.indexFaces(collectionId, referenceImage, UUID.randomUUID().toString());
        String faceId = ((FaceCollectionClient.IndexResult.Ok) indexed).faceId();
        Enrollment enrollment = enrollments.save(new Enrollment(
                UUID.randomUUID(),
                orgId,
                "user-1",
                EnrollmentSource.IDV,
                UUID.randomUUID(),
                faceId,
                collectionId,
                now));
        return new Fixture(orgId, authKey, enrollment.getId(), null);
    }

    private String issueKey(UUID orgId, UUID integrationId) {
        String raw = CryptoTokens.randomApiKey(true);
        apiKeys.save(new ApiKey(
                UUID.randomUUID(),
                orgId,
                integrationId,
                raw.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(raw),
                Instant.now()));
        return raw;
    }

    private static final class Fixture {
        private final UUID orgId;
        private final String authKey;
        private final UUID enrollmentId;
        private UUID authenticationId;

        private Fixture(UUID orgId, String authKey, UUID enrollmentId, UUID authenticationId) {
            this.orgId = orgId;
            this.authKey = authKey;
            this.enrollmentId = enrollmentId;
            this.authenticationId = authenticationId;
        }

        UUID orgId() {
            return orgId;
        }

        String authKey() {
            return authKey;
        }

        UUID enrollmentId() {
            return enrollmentId;
        }

        UUID authenticationId() {
            return authenticationId;
        }
    }

    @TestConfiguration
    static class AuthFlowFakes {
        @Bean
        @Primary
        FaceLivenessClient scriptedAuthLiveness() {
            return new FaceLivenessClient() {
                @Override
                public String createSession(String clientRequestToken) {
                    return "auth-sess-" + CREATES.incrementAndGet();
                }

                @Override
                public LivenessResult getResults(String sessionId) {
                    FaceLivenessClient.LivenessResult next = NEXT_RESULT.get();
                    return next == null
                            ? new FaceLivenessClient.LivenessResult(0.95, "SUCCEEDED", FACE_A)
                            : next;
                }
            };
        }

        @Bean
        @Primary
        LivenessCredentialPort testCredentials() {
            return () -> new LivenessCredentialPort.Issued(
                    "AKIA", "secret", "token", Instant.parse("2026-11-22T18:03:00Z"));
        }
    }
}
