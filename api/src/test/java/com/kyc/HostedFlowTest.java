package com.kyc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.ApiKey;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Consent;
import com.kyc.entities.ConsentDecision;
import com.kyc.entities.Organization;
import com.kyc.entities.VerificationStatus;
import com.kyc.ports.HostedTokenStore;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.ConsentRepository;
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
class HostedFlowTest {

    private static final String RAW_KEY = "ky_test_localdev1";
    private static final String APPLICANT_IP = "203.0.113.10";

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
    private ConsentRepository consentRepository;

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
    void unknownTokenIsNotFound() throws Exception {
        mockMvc.perform(get("/v1/flow/unknown-token-xx"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void firstOpenMovesToPendingConsentWithoutPii() throws Exception {
        Created created = createVerification();

        mockMvc.perform(get("/v1/flow/" + created.token()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.verification_id").value(created.id().toString()))
                .andExpect(jsonPath("$.status").value("pending_consent"))
                .andExpect(jsonPath("$.consent_text_version").value("consent-v1"))
                .andExpect(jsonPath("$.organization_id").doesNotExist())
                .andExpect(jsonPath("$.external_id").doesNotExist())
                .andExpect(jsonPath("$.applicant").doesNotExist());

        mockMvc.perform(get("/v1/flow/" + created.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending_consent"));

        List<String> actions = auditEventRepository.findByResourceIdOrderByIdAsc(created.id()).stream()
                .map(AuditEvent::getAction)
                .toList();
        assertEquals(1, actions.stream().filter("hosted_link.opened"::equals).count());
        assertEquals(
                VerificationStatus.PENDING_CONSENT,
                verificationRepository
                        .findByIdAndOrganizationId(created.id(), organizationId)
                        .orElseThrow()
                        .getStatus());
    }

    @Test
    void missingRedisTokenWithKnownHashIsGone() throws Exception {
        Created created = createVerification();
        hostedTokenStore.delete(created.id());

        mockMvc.perform(get("/v1/flow/" + created.token()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("hosted_link_expired"));
        assertEquals(
                VerificationStatus.EXPIRED,
                verificationRepository
                        .findByIdAndOrganizationId(created.id(), organizationId)
                        .orElseThrow()
                        .getStatus());
    }

    @Test
    void acceptConsentHashesIpAndDoesNotStorePlaintext() throws Exception {
        Created created = createVerification();
        mockMvc.perform(get("/v1/flow/" + created.token())).andExpect(status().isOk());

        mockMvc.perform(post("/v1/flow/" + created.token() + "/consent")
                        .with(request -> {
                            request.setRemoteAddr(APPLICANT_IP);
                            return request;
                        })
                        .header("User-Agent", "FlowTest/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending_applicant"))
                .andExpect(jsonPath("$.next").value("capture_unavailable"));

        Consent consent = consentRepository
                .findByVerificationIdAndOrganizationId(created.id(), organizationId)
                .orElseThrow();
        assertEquals(ConsentDecision.ACCEPTED, consent.getDecision());
        assertEquals(64, consent.getIpHash().length());
        assertNotEquals(APPLICANT_IP, consent.getIpHash());
        assertFalse(consent.getIpHash().contains("203"));
        assertEquals("FlowTest/1.0", consent.getUserAgent());
        assertNull(consentRepository
                .findByVerificationIdAndOrganizationId(created.id(), organizationId)
                .map(Consent::getIpHash)
                .filter(APPLICANT_IP::equals)
                .orElse(null));

        mockMvc.perform(post("/v1/flow/" + created.token() + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"declined\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("consent_already_recorded"));
    }

    @Test
    void declineConsentSetsDeclined() throws Exception {
        Created created = createVerification();

        mockMvc.perform(post("/v1/flow/" + created.token() + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"declined\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("declined"));

        assertEquals(
                VerificationStatus.DECLINED,
                verificationRepository
                        .findByIdAndOrganizationId(created.id(), organizationId)
                        .orElseThrow()
                        .getStatus());
        assertEquals(
                ConsentDecision.DECLINED,
                consentRepository
                        .findByVerificationIdAndOrganizationId(created.id(), organizationId)
                        .orElseThrow()
                        .getDecision());
    }

    private Created createVerification() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_id\":\"flow-user\",\"applicant\":{\"email\":\"secret@example.com\"}}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
        String hostedUrl = com.jayway.jsonpath.JsonPath.read(body, "$.hosted_url");
        String token = hostedUrl.substring(hostedUrl.lastIndexOf('/') + 1);
        return new Created(id, token);
    }

    private record Created(UUID id, String token) {}
}
