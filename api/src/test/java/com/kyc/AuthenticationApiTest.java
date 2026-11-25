package com.kyc;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.ApiKey;
import com.kyc.entities.CreditAccount;
import com.kyc.entities.Enrollment;
import com.kyc.entities.Integration;
import com.kyc.entities.Organization;
import com.kyc.enums.AuthOnBorderline;
import com.kyc.enums.EnrollmentSource;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuthenticationRepository;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.EnrollmentRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import com.kyc.services.CryptoTokens;
import com.kyc.services.auth.AuthPolicyService;
import java.math.BigDecimal;
import java.time.Instant;
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
class AuthenticationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private EnrollmentRepository enrollments;

    @Autowired
    private AuthenticationRepository authentications;

    @Autowired
    private CreditAccountRepository credits;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthPolicyService policies;

    @Test
    void withoutEnrollmentReturnsNoEnrollment() throws Exception {
        Fixture fixture = seed(false, 5000);
        mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-no-enroll-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"missing-user\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("no_enrollment"));
        assertEquals(0, authentications.count());
    }

    @Test
    void withEnrollmentCreatesSessionAndHostedUrl() throws Exception {
        Fixture fixture = seed(true, 5000);
        MvcResult created = mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-create-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\",\"metadata\":{\"purpose\":\"login\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.engine").value("auth-2"))
                .andExpect(jsonPath("$.policy_version").value("auth-policy-0"))
                .andExpect(jsonPath("$.integration_mode").value("live"))
                .andExpect(jsonPath("$.enrollment_id").value(fixture.enrollmentId().toString()))
                .andExpect(jsonPath("$.hosted_url").value(startsWith("http://")))
                .andExpect(jsonPath("$.face_id").doesNotExist())
                .andReturn();
        String id = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/v1/authentications/" + id).header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.external_id").value("user-1"));
    }

    @Test
    void idempotencyReplaysSameSession() throws Exception {
        Fixture fixture = seed(true, 5000);
        MvcResult first = mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-replay-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = IdvSupport.JSON.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-replay-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        assertEquals(1, authentications.count());

        mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-replay-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\",\"metadata\":{\"x\":1}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("idempotency_key_conflict"));
    }

    @Test
    void insufficientCreditCreatesNothing() throws Exception {
        Fixture fixture = seed(true, 0);
        mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-credit-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("insufficient_credit"));
        assertEquals(0, authentications.count());
    }

    @Test
    void sandboxScenarioIsInvalid() throws Exception {
        Fixture fixture = seed(true, 5000);
        mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-sandbox-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\",\"sandbox_scenario\":\"approved\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid"));
        assertEquals(0, authentications.count());
    }

    @Test
    void identityKeyIsUnauthorized() throws Exception {
        Fixture fixture = seed(true, 5000);
        mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.identityKey())
                        .header("Idempotency-Key", "idem-idv-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void cancelThenGetShowsCancelled() throws Exception {
        Fixture fixture = seed(true, 5000);
        MvcResult created = mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", "idem-cancel-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/v1/authentications/" + id + "/cancel")
                        .header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));

        mockMvc.perform(get("/v1/authentications/" + id).header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.hosted_url").doesNotExist());
    }

    @Test
    void reviewIsNotAvailable() throws Exception {
        Fixture fixture = seed(true, 5000);
        String id = createSession(fixture, "idem-review-01");

        mockMvc.perform(post("/v1/authentications/" + id + "/review")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("invalid_status"));
    }

    @Test
    void publishedPolicyAppliesToNewSessionsOnly() throws Exception {
        Fixture fixture = seed(true, 5000);
        String first = createSession(fixture, "idem-policy-old");
        mockMvc.perform(get("/v1/authentications/" + first).header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy_version").value("auth-policy-0"));

        var integration = integrations.findById(authentications.findById(UUID.fromString(first)).orElseThrow().getIntegrationId())
                .orElseThrow();
        policies.publish(
                fixture.orgId(),
                UUID.randomUUID(),
                integration.getId(),
                new BigDecimal("0.950"),
                new BigDecimal("0.850"),
                AuthOnBorderline.DECLINE,
                2);

        String second = createSession(fixture, "idem-policy-new");
        mockMvc.perform(get("/v1/authentications/" + first).header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(jsonPath("$.policy_version").value("auth-policy-0"));
        mockMvc.perform(get("/v1/authentications/" + second).header("Authorization", "Bearer " + fixture.authKey()))
                .andExpect(jsonPath("$.policy_version").value("auth-policy-1"));
    }

    private String createSession(Fixture fixture, String idempotencyKey) throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/authentications")
                        .header("Authorization", "Bearer " + fixture.authKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"user-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private Fixture seed(boolean withEnrollment, long balanceMinor) {
        Instant now = Instant.parse("2026-11-22T18:00:00Z");
        UUID orgId = UUID.randomUUID();
        organizations.save(new Organization(orgId, "Auth API", "auth-api-" + orgId, now));
        if (balanceMinor > 0) {
            credits.save(new CreditAccount(orgId, "usd", balanceMinor, now));
        } else {
            credits.save(new CreditAccount(orgId, "usd", 0, now));
        }
        Integration identity = integrations.save(new Integration(
                UUID.randomUUID(), orgId, ProductCode.IDENTITY, IntegrationMode.LIVE, "IDV", now));
        Integration auth = integrations.save(new Integration(
                UUID.randomUUID(), orgId, ProductCode.AUTHENTICATION, IntegrationMode.LIVE, "Auth", now));
        String identityKey = issueKey(orgId, identity.getId(), false);
        String authKey = issueKey(orgId, auth.getId(), true);
        UUID enrollmentId = null;
        if (withEnrollment) {
            Enrollment enrollment = enrollments.save(new Enrollment(
                    UUID.randomUUID(),
                    orgId,
                    "user-1",
                    EnrollmentSource.IDV,
                    UUID.randomUUID(),
                    "face-" + orgId,
                    "rm-auth-" + orgId,
                    now));
            enrollmentId = enrollment.getId();
        }
        return new Fixture(orgId, authKey, identityKey, enrollmentId);
    }

    private String issueKey(UUID orgId, UUID integrationId, boolean live) {
        String raw = CryptoTokens.randomApiKey(live);
        apiKeys.save(new ApiKey(
                UUID.randomUUID(),
                orgId,
                integrationId,
                raw.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(raw),
                Instant.now()));
        return raw;
    }

    private record Fixture(UUID orgId, String authKey, String identityKey, UUID enrollmentId) {}
}
