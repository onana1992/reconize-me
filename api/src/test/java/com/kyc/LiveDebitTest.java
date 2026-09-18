package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.StripeCustomer;
import com.kyc.ports.MailPort;
import com.kyc.repositories.StripeCustomerRepository;
import jakarta.servlet.http.Cookie;
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

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + liveKey)
                        .header("Idempotency-Key", "live-debit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(jsonPath("$.balance_minor").value(4100))
                .andExpect(jsonPath("$.usage[0].sandbox_count").value(2))
                .andExpect(jsonPath("$.usage[0].live_count").value(1))
                .andExpect(jsonPath("$.usage[0].live_debit_minor").value(900));

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + liveKey)
                        .header("Idempotency-Key", "live-debit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/console/billing").cookie(owner)).andExpect(jsonPath("$.balance_minor").value(4100));

        mockMvc.perform(get("/v1/usage").header("Authorization", "Bearer " + liveKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance_minor").value(4100))
                .andExpect(jsonPath("$.usage[0].live_count").value(1))
                .andExpect(jsonPath("$.usage[0].live_debit_minor").value(900));
        mockMvc.perform(get("/v1/usage").header("Authorization", "Bearer " + testKey))
                .andExpect(jsonPath("$.balance_minor").value(4100));

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
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/v1/verifications")
                            .header("Authorization", "Bearer " + liveKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(get("/v1/console/billing").cookie(owner)).andExpect(jsonPath("$.balance_minor").value(500));
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
        mockMvc.perform(get("/v1/console/billing").cookie(owner)).andExpect(jsonPath("$.balance_minor").value(500));
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
