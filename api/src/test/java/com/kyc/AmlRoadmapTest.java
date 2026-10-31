package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.Integration;
import com.kyc.entities.Organization;
import com.kyc.entities.Screening;
import com.kyc.entities.Verification;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.ports.MailPort;
import com.kyc.ports.SanctionsMatchPort;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.ScreeningRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.services.aml.AmlAttachmentService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AmlRoadmapTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private SanctionsMatchPort sanctionsMatch;

    @Autowired
    private ScreeningRepository screenings;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private VerificationRepository verifications;

    @Autowired
    private AmlAttachmentService attachment;

    @Test
    void liveAmlIsRefusedBeforeCreditAndKeysStayIsolated() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "aml-a@example.com", "Aml A");
        Cookie other = AccountSupport.signupVerified(mockMvc, mailPort, "aml-b@example.com", "Aml B");

        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"aml\",\"mode\":\"live\",\"name\":\"Official\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("engine_unavailable"));

        MvcResult created = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"aml\",\"mode\":\"test\",\"name\":\"Sandbox lists\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product").value("aml"))
                .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("ky_test_")))
                .andReturn();
        String id = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/v1/console/integrations/" + id).cookie(other)).andExpect(status().isNotFound());
    }

    @Test
    void ivanIsConfirmedAdaIsClearAndIdempotencyDoesNotInsertTwice() throws Exception {
        Issued aml = amlKey("aml-screen@example.com", "Aml Screen");
        MvcResult ivan = postScreening(aml.key(), ivanBody("1971-04-12"), "idem-ivan-01");
        assertEquals(201, ivan.getResponse().getStatus());
        var ivanJson = IdvSupport.JSON.readTree(ivan.getResponse().getContentAsString());
        assertEquals("confirmed_match", ivanJson.get("outcome").asText());
        assertEquals("aml-1", ivanJson.get("engine").asText());
        assertEquals("fx-sanction-ivan", ivanJson.get("hits").get(0).get("entity_id").asText());
        assertFalse(ivan.getResponse().getContentAsString().contains("Sanctionexample") == false);

        MvcResult replay = postScreening(aml.key(), ivanBody("1971-04-12"), "idem-ivan-01");
        assertEquals(200, replay.getResponse().getStatus());
        assertEquals(ivanJson.get("id").asText(), IdvSupport.JSON.readTree(replay.getResponse().getContentAsString()).get("id").asText());
        assertEquals(1, count(aml.organizationId(), UUID.fromString(aml.integrationId())));

        mockMvc.perform(post("/v1/screenings")
                        .header("Authorization", "Bearer " + aml.key())
                        .header("Idempotency-Key", "idem-ivan-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adaBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("idempotency_key_conflict"));

        MvcResult ada = postScreening(aml.key(), adaBody(), null);
        assertEquals(201, ada.getResponse().getStatus());
        var adaJson = IdvSupport.JSON.readTree(ada.getResponse().getContentAsString());
        assertEquals("clear", adaJson.get("outcome").asText());
        assertEquals(0, adaJson.get("hit_count").asInt());
        assertTrue(adaJson.get("hits").isEmpty());

        mockMvc.perform(post("/v1/screenings")
                        .header("Authorization", "Bearer " + aml.identityKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adaBody()))
                .andExpect(status().isUnauthorized());

        Issued other = amlKey("aml-other@example.com", "Aml Other");
        mockMvc.perform(get("/v1/screenings/" + ivanJson.get("id").asText())
                        .header("Authorization", "Bearer " + other.key()))
                .andExpect(status().isNotFound());
    }

    @Test
    void dispositionRewritesOutcomeKeepsTheNoteOutOfAuditAndSkipsHomonyms() throws Exception {
        Issued aml = amlKey("aml-disp@example.com", "Aml Disp");
        String noraId = IdvSupport.JSON
                .readTree(postScreening(aml.key(), noraBody("1982-11-03"), null).getResponse().getContentAsString())
                .get("id")
                .asText();
        String hitId = IdvSupport.JSON
                .readTree(mockMvc.perform(get("/v1/screenings/" + noraId).header("Authorization", "Bearer " + aml.key()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("hits")
                .get(0)
                .get("id")
                .asText();

        mockMvc.perform(post("/v1/screenings/" + noraId + "/hits/" + hitId + "/disposition")
                        .header("Authorization", "Bearer " + aml.key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"false_positive\",\"reason\":\"different_person\",\"note\":\"Nora is not the listed person\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("clear"));

        mockMvc.perform(post("/v1/screenings/" + noraId + "/hits/" + hitId + "/disposition")
                        .header("Authorization", "Bearer " + aml.key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"false_positive\",\"reason\":\"name_only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("clear"));

        var audits = auditEvents.findByResourceIdOrderByIdAsc(UUID.fromString(noraId)).stream()
                .filter(event -> "screening.disposition_set".equals(event.getAction()))
                .toList();
        assertEquals(2, audits.size());
        assertFalse(audits.get(0).getPayload().contains("Nora"));
        assertFalse(audits.get(0).getPayload().contains("note"));

        String homonym = IdvSupport.JSON
                .readTree(postScreening(aml.key(), noraBody("1982-11-04"), null).getResponse().getContentAsString())
                .get("id")
                .asText();
        mockMvc.perform(get("/v1/screenings/" + homonym).header("Authorization", "Bearer " + aml.key()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].prior_disposition").doesNotExist());

        String confirmed = IdvSupport.JSON
                .readTree(postScreening(aml.key(), noraBody(null), "idem-nora-true").getResponse().getContentAsString())
                .get("id")
                .asText();
        String confirmedHit = IdvSupport.JSON
                .readTree(mockMvc.perform(get("/v1/screenings/" + confirmed).header("Authorization", "Bearer " + aml.key()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("hits")
                .get(0)
                .get("id")
                .asText();
        mockMvc.perform(post("/v1/screenings/" + confirmed + "/hits/" + confirmedHit + "/disposition")
                        .header("Authorization", "Bearer " + aml.key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"true_positive\",\"reason\":\"same_person\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("confirmed_match"));

        Screening failed = new Screening(
                UUID.randomUUID(),
                aml.organizationId(),
                UUID.fromString(aml.integrationId()),
                null,
                null,
                ScreeningOutcome.CLEAR,
                "aml-policy-0",
                "x",
                "Ada",
                "Clear",
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-10-20T00:00:00Z"));
        failed.fail("provider_timeout", Instant.parse("2026-10-20T00:00:01Z"));
        screenings.save(failed);
        mockMvc.perform(post("/v1/screenings/" + failed.getId() + "/hits/" + UUID.randomUUID() + "/disposition")
                        .header("Authorization", "Bearer " + aml.key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"false_positive\",\"reason\":\"different_person\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("invalid_status"));
    }

    @Test
    void memberCanQualifyAndDeveloperCannot() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "aml-role@example.com", "Aml Role");
        MvcResult created = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"aml\",\"mode\":\"test\",\"name\":\"Lists\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String key = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("key").asText();
        var screening = IdvSupport.JSON.readTree(postScreening(key, noraBody("1982-11-03"), null).getResponse().getContentAsString());
        String screeningId = screening.get("id").asText();
        String hitId = screening.get("hits").get(0).get("id").asText();

        AccountSupport.invite(mockMvc, owner, "aml-member@example.com", "member");
        Cookie member = AccountSupport.acceptInvite(mockMvc, mailPort, "aml-member@example.com");
        mockMvc.perform(post("/v1/console/screenings/" + screeningId + "/hits/" + hitId + "/disposition")
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"false_positive\",\"reason\":\"different_person\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("clear"));

        AccountSupport.invite(mockMvc, owner, "aml-dev@example.com", "developer");
        Cookie developer = AccountSupport.acceptInvite(mockMvc, mailPort, "aml-dev@example.com");
        mockMvc.perform(post("/v1/console/screenings/" + screeningId + "/hits/" + hitId + "/disposition")
                        .cookie(developer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"true_positive\",\"reason\":\"same_person\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/console/screenings/" + screeningId).cookie(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("clear"));

        mockMvc.perform(get("/v1/screenings/" + screeningId).header("Authorization", "Bearer " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("clear"))
                .andExpect(jsonPath("$.hits[0].disposition").value("false_positive"));
    }

    @Test
    void productionPolicyDoesNotChangeSandboxScreening() throws Exception {
        Issued aml = amlKey("aml-policy@example.com", "Aml Policy");
        Integration live = integrations.save(new Integration(
                UUID.randomUUID(),
                aml.organizationId(),
                ProductCode.AML,
                IntegrationMode.LIVE,
                "Production lists",
                Instant.now()));
        MvcResult first = postScreening(aml.key(), ivanPartialBody(), null);
        assertEquals("clear", IdvSupport.JSON.readTree(first.getResponse().getContentAsString()).get("outcome").asText());
        String firstId = IdvSupport.JSON.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/v1/console/aml/policy")
                        .cookie(aml.owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"review_threshold":0.90,"confirm_threshold":0.95,"categories":["sanction","pep"]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/console/aml/policy")
                        .cookie(aml.owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"review_threshold":0.70,"confirm_threshold":0.95,"categories":["pep"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("categories"));

        mockMvc.perform(post("/v1/console/aml/policy")
                        .cookie(aml.owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"integration_id":"%s","review_threshold":0.50,"confirm_threshold":0.95,"categories":["sanction","pep","rca","debarment"],"attach_to_identity":false,"on_match":"review"}
                                """.formatted(live.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("aml-policy-1"));

        MvcResult second = postScreening(aml.key(), ivanPartialBody(), null);
        assertEquals("clear", IdvSupport.JSON.readTree(second.getResponse().getContentAsString()).get("outcome").asText());
        mockMvc.perform(get("/v1/screenings/" + firstId).header("Authorization", "Bearer " + aml.key()))
                .andExpect(jsonPath("$.outcome").value("clear"))
                .andExpect(jsonPath("$.policy_version").value("aml-policy-0"));
        mockMvc.perform(get("/v1/console/aml/policy")
                        .param("integration_id", live.getId().toString())
                        .cookie(aml.owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("aml-policy-1"));
    }

    @Test
    void sandboxFlowIgnoresProductionIdentityRules() throws Exception {
        assertThrows(IllegalStateException.class, () -> sanctionsMatch.match(null));
        Issued aml = amlKey("aml-attach@example.com", "Aml Attach");
        Integration live = integrations.save(new Integration(
                UUID.randomUUID(),
                aml.organizationId(),
                ProductCode.IDENTITY,
                IntegrationMode.LIVE,
                "Production",
                Instant.now()));
        enableIdentityScreening(aml.owner(), live.getId());

        var created = IdvSupport.create(
                mockMvc,
                aml.identityKey(),
                "{\"metadata\":{\"sandbox_scenario\":\"screening_nora\"}}");
        String token = IdvSupport.token(created);
        String verificationId = created.path("id").asText();
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        var done = IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
        assertEquals("approved", done.path("status").asText());

        mockMvc.perform(get("/v1/verifications/" + verificationId).header("Authorization", "Bearer " + aml.identityKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("approved"))
                .andExpect(jsonPath("$.screening").doesNotExist())
                .andExpect(jsonPath("$.policy_version").doesNotExist());
        assertEquals(0, count(aml.organizationId(), null));
    }

    @Test
    void liveIdentityWithAttachDoesNotCreateAFixtureScreening() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        UUID orgId = UUID.randomUUID();
        organizations.save(new Organization(orgId, "Live Attach", "live-attach-" + orgId.toString().substring(0, 8), now));
        Integration live = integrations.save(new Integration(
                UUID.randomUUID(), orgId, ProductCode.IDENTITY, IntegrationMode.LIVE, "Production", now));
        Verification verification = verifications.save(new Verification(
                UUID.randomUUID(),
                orgId,
                live.getId(),
                null,
                "Nora",
                "Potentialexample",
                null,
                null,
                "hash",
                now.plusSeconds(3600),
                null,
                now));
        verification.decide(
                VerificationDecision.APPROVED,
                "[\"liveness_pass\"]",
                "idv-1",
                "{\"first_name\":\"Nora\",\"last_name\":\"Potentialexample\",\"birth_date\":\"1982-11-03\",\"document_country\":\"FR\"}",
                now);
        Integration lists = integrations.save(new Integration(
                UUID.randomUUID(), orgId, ProductCode.AML, IntegrationMode.LIVE, "Lists", now));
        policies.save(new com.kyc.entities.AmlPolicyEntity(
                UUID.randomUUID(),
                orgId,
                lists.getId(),
                1,
                new java.math.BigDecimal("0.700"),
                new java.math.BigDecimal("0.950"),
                "sanction,pep",
                true,
                com.kyc.enums.AmlOnMatch.REVIEW,
                null,
                null,
                UUID.randomUUID(),
                now));
        attachment.attach(verification, live, now);
        assertEquals(VerificationDecision.REVIEW, verification.getDecision());
        assertTrue(verification.getDecisionReasons().contains("aml_unavailable"));
        assertEquals(0, count(orgId, null));
    }

    @Autowired
    private com.kyc.repositories.AmlPolicyRepository policies;

    private Issued amlKey(String email, String org) throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, email, org);
        MvcResult identity = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"test\",\"name\":\"Identity\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult aml = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"aml\",\"mode\":\"test\",\"name\":\"Lists\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        var identityJson = IdvSupport.JSON.readTree(identity.getResponse().getContentAsString());
        var amlJson = IdvSupport.JSON.readTree(aml.getResponse().getContentAsString());
        return new Issued(
                owner,
                AccountSupport.organizationId(mockMvc, owner),
                amlJson.get("id").asText(),
                amlJson.get("key").asText(),
                identityJson.get("key").asText());
    }

    private MvcResult postScreening(String key, String body, String idempotency) throws Exception {
        var request = post("/v1/screenings")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (idempotency != null) {
            request.header("Idempotency-Key", idempotency);
        }
        return mockMvc.perform(request).andReturn();
    }

    private int count(UUID organizationId, UUID integrationId) {
        return screenings
                .page(
                        organizationId,
                        integrationId,
                        null,
                        null,
                        null,
                        Instant.parse("9999-12-31T00:00:00Z"),
                        new UUID(Long.MAX_VALUE, Long.MAX_VALUE),
                        PageRequest.of(0, 50))
                .size();
    }

    private void enableIdentityScreening(Cookie owner, UUID integrationId) throws Exception {
        String raw = mockMvc.perform(get("/v1/console/identity/policy")
                        .param("integration_id", integrationId.toString())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var policy = IdvSupport.JSON.readTree(raw);
        var body = IdvSupport.JSON.createObjectNode();
        body.put("integration_id", integrationId.toString());
        body.set("profile", policy.get("profile"));
        var codes = body.putArray("document_codes");
        for (var document : policy.get("documents")) {
            if (document.path("enabled").asBoolean()) {
                codes.add(document.path("code").asText());
            }
        }
        var flow = policy.get("flow");
        var flowBody = body.putObject("flow");
        flowBody.put("link_ttl_seconds", flow.path("link_ttl_seconds").asInt());
        flowBody.put("max_capture_attempts", flow.path("max_capture_attempts").asInt());
        flowBody.put("max_liveness_attempts", flow.path("max_liveness_attempts").asInt());
        var screening = body.putObject("screening");
        screening.put("enabled", true);
        screening.put("on_match", "review");
        mockMvc.perform(post("/v1/console/identity/policy")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated());
    }

    private static String ivanBody(String dob) {
        return "{\"type\":\"person\",\"first_name\":\"Ivan\",\"last_name\":\"Sanctionexample\",\"date_of_birth\":\""
                + dob + "\",\"id_number\":\"X1234567\",\"id_type\":\"passport\"}";
    }

    private static String ivanPartialBody() {
        return "{\"first_name\":\"Ivan\",\"last_name\":\"Sanction\",\"date_of_birth\":\"1971-04-12\"}";
    }

    private static String adaBody() {
        return "{\"first_name\":\"Ada\",\"last_name\":\"Clear\",\"date_of_birth\":\"1991-02-02\"}";
    }

    private static String noraBody(String dob) {
        String birth = dob == null ? "" : ",\"date_of_birth\":\"" + dob + "\"";
        return "{\"first_name\":\"Nora\",\"last_name\":\"Potentialexample\"" + birth + "}";
    }

    private record Issued(Cookie owner, UUID organizationId, String integrationId, String key, String identityKey) {}
}
