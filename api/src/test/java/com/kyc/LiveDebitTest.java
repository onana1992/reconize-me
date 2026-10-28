package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kyc.entities.CreditAccount;
import com.kyc.entities.StripeCustomer;
import com.kyc.ports.MailPort;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.StripeCustomerRepository;
import com.kyc.services.CreditService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LiveDebitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private StripeCustomerRepository stripeCustomers;

    @Autowired
    private CreditAccountRepository creditAccounts;

    @Autowired
    private CreditService creditService;

    @Test
    void testNeverDebitsLiveDebitsAndUsageMatchesBilling() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "debit-owner@example.com", "Debit Co");
        String testKey = IdvSupport.JSON
                .readTree(mockMvc.perform(post("/v1/console/integrations")
                                .cookie(owner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"test\",\"name\":\"API Test\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("key")
                .asText();
        String testId = AccountSupport.createIntegration(mockMvc, owner, "Console Test").get("id").asText();

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + testKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/console/verifications")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"integration_id\":\"" + testId + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/v1/console/billing").cookie(owner)).andExpect(jsonPath("$.balance_minor").value(0));

        credit(owner, 5000);
        String liveKey = IdvSupport.JSON
                .readTree(mockMvc.perform(post("/v1/console/integrations")
                                .cookie(owner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"live\",\"name\":\"Production\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("key")
                .asText();

        MvcResult created = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + liveKey)
                        .header("Idempotency-Key", "live-debit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(jsonPath("$.balance_minor").value(5000))
                .andExpect(jsonPath("$.usage[0].sandbox_count").value(2))
                .andExpect(jsonPath("$.usage[0].live_count").value(1))
                .andExpect(jsonPath("$.usage[0].live_debit_minor").value(0));

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + liveKey)
                        .header("Idempotency-Key", "live-debit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/console/billing").cookie(owner)).andExpect(jsonPath("$.balance_minor").value(5000));

        String token = IdvSupport.token(IdvSupport.JSON.readTree(created.getResponse().getContentAsString()));
        decideByExhaustingCapture(token);
        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(jsonPath("$.balance_minor").value(4920))
                .andExpect(jsonPath("$.usage[0].live_debit_minor").value(80));

        mockMvc.perform(get("/v1/usage").header("Authorization", "Bearer " + liveKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance_minor").value(4920))
                .andExpect(jsonPath("$.usage[0].live_count").value(1))
                .andExpect(jsonPath("$.usage[0].live_debit_minor").value(80));
        mockMvc.perform(get("/v1/usage").header("Authorization", "Bearer " + testKey))
                .andExpect(jsonPath("$.balance_minor").value(4920));

        mockMvc.perform(get("/v1/usage")).andExpect(status().isUnauthorized());

        Cookie other = AccountSupport.signupVerified(mockMvc, mailPort, "debit-b@example.com", "Debit B");
        String otherKey = IdvSupport.JSON
                .readTree(mockMvc.perform(post("/v1/console/integrations")
                                .cookie(other)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"test\",\"name\":\"Other\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("key")
                .asText();
        mockMvc.perform(get("/v1/usage").header("Authorization", "Bearer " + otherKey))
                .andExpect(jsonPath("$.balance_minor").value(0));
    }

    @Test
    void insufficientCreditBlocksLiveCreateNotTest() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "debit-low@example.com", "Low Co");
        credit(owner, 5000);
        String liveKey = IdvSupport.JSON
                .readTree(mockMvc.perform(post("/v1/console/integrations")
                                .cookie(owner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"live\",\"name\":\"Prod\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("key")
                .asText();
        setBalance(owner, 50);
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + liveKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("insufficient_credit"));
        String testKey = IdvSupport.JSON
                .readTree(mockMvc.perform(post("/v1/console/integrations")
                                .cookie(owner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"test\",\"name\":\"Still Test\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("key")
                .asText();
        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + testKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/v1/console/billing").cookie(owner)).andExpect(jsonPath("$.balance_minor").value(50));
    }

    @Test
    void identityWithScreeningDebitsOneThirtyAndAmlDebitsFifty() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "debit-prices@example.com", "Price Co");
        credit(owner, 5000);
        enableIdentityScreening(owner);
        String liveKey = IdvSupport.JSON
                .readTree(mockMvc.perform(post("/v1/console/integrations")
                                .cookie(owner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"live\",\"name\":\"Production\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("key")
                .asText();
        MvcResult created = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + liveKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = IdvSupport.token(IdvSupport.JSON.readTree(created.getResponse().getContentAsString()));
        decideByExhaustingCapture(token);
        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(jsonPath("$.balance_minor").value(4870))
                .andExpect(jsonPath("$.usage[0].live_debit_minor").value(130));

        UUID screeningId = UUID.randomUUID();
        creditService.debitForLiveScreening(AccountSupport.organizationId(mockMvc, owner), screeningId, null, "system");
        creditService.debitForLiveScreening(AccountSupport.organizationId(mockMvc, owner), screeningId, null, "system");
        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(jsonPath("$.balance_minor").value(4820))
                .andExpect(jsonPath("$.usage[1].product").value("aml"))
                .andExpect(jsonPath("$.usage[1].live_debit_minor").value(50));
    }

    private void decideByExhaustingCapture(String token) throws Exception {
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        byte[] tiny = IdvSupport.tinyJpeg();
        JsonNode done = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            done = IdvSupport.captureDocument(mockMvc, token, tiny);
            String status = done.path("status").asText();
            if ("approved".equals(status) || "declined".equals(status) || "review".equals(status)) {
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("verification did not reach a decision: " + done);
    }

    private void setBalance(Cookie owner, long balance) throws Exception {
        CreditAccount account = creditAccounts.findById(AccountSupport.organizationId(mockMvc, owner)).orElseThrow();
        account.apply(balance - account.getBalanceMinor(), Instant.now());
        creditAccounts.saveAndFlush(account);
    }

    private void enableIdentityScreening(Cookie owner) throws Exception {
        String raw = mockMvc.perform(get("/v1/console/identity/policy").cookie(owner))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode policy = IdvSupport.JSON.readTree(raw);
        var body = IdvSupport.JSON.createObjectNode();
        body.set("profile", policy.get("profile"));
        var codes = body.putArray("document_codes");
        for (JsonNode document : policy.get("documents")) {
            if (document.path("enabled").asBoolean()) {
                codes.add(document.path("code").asText());
            }
        }
        JsonNode flow = policy.get("flow");
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
}
