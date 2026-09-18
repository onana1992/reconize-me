package com.kyc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
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
class StripeCheckoutTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private StripeCustomerRepository stripeCustomers;

    @Test
    void unsignedWebhookIsRejectedAndReplayDoesNotDoubleCredit() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "stripe-owner@example.com", "Stripe Co");
        UUID orgId = AccountSupport.organizationId(mockMvc, owner);

        mockMvc.perform(post("/v1/console/billing/checkout")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pack_minor\":123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));

        MvcResult checkout = mockMvc.perform(post("/v1/console/billing/checkout")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pack_minor\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(startsWith("https://checkout.stripe.com/")))
                .andReturn();
        String url = IdvSupport.JSON.readTree(checkout.getResponse().getContentAsString()).get("url").asText();
        String sessionId = url.substring(url.lastIndexOf('/') + 1);
        String customerId = stripeCustomers
                .findById(orgId)
                .map(StripeCustomer::getStripeCustomerId)
                .orElseThrow();
        String actorId = UUID.randomUUID().toString();
        String payload = StripeWebhookSupport.checkoutCompleted(
                "evt_replay_1", sessionId, customerId, orgId.toString(), actorId, 5000);

        mockMvc.perform(post("/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_signature"));

        mockMvc.perform(post("/v1/webhooks/stripe")
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_signature"));

        mockMvc.perform(post("/v1/webhooks/stripe")
                        .header("Stripe-Signature", StripeWebhookSupport.signedHeader(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance_minor").value(5000))
                .andExpect(jsonPath("$.live_unlocked").value(true))
                .andExpect(jsonPath("$.ledger.entries", hasSize(1)))
                .andExpect(jsonPath("$.ledger.entries[0].entry_type").value("topup"));

        mockMvc.perform(post("/v1/webhooks/stripe")
                        .header("Stripe-Signature", StripeWebhookSupport.signedHeader(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(jsonPath("$.balance_minor").value(5000))
                .andExpect(jsonPath("$.ledger.entries", hasSize(1)));
    }

    @Test
    void metadataOrganizationMismatchIsIgnored() throws Exception {
        Cookie ownerA = AccountSupport.signupVerified(mockMvc, mailPort, "stripe-a@example.com", "Stripe A");
        Cookie ownerB = AccountSupport.signupVerified(mockMvc, mailPort, "stripe-b@example.com", "Stripe B");
        UUID orgA = AccountSupport.organizationId(mockMvc, ownerA);
        UUID orgB = AccountSupport.organizationId(mockMvc, ownerB);
        String userA = UUID.randomUUID().toString();

        mockMvc.perform(post("/v1/console/billing/checkout")
                        .cookie(ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pack_minor\":5000}"))
                .andExpect(status().isCreated());
        String customerA = stripeCustomers.findById(orgA).orElseThrow().getStripeCustomerId();
        String payload = StripeWebhookSupport.checkoutCompleted(
                "evt_mismatch", "cs_mismatch", customerA, orgB.toString(), userA, 5000);
        mockMvc.perform(post("/v1/webhooks/stripe")
                        .header("Stripe-Signature", StripeWebhookSupport.signedHeader(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/console/billing").cookie(ownerA)).andExpect(jsonPath("$.balance_minor").value(0));
        mockMvc.perform(get("/v1/console/billing").cookie(ownerB)).andExpect(jsonPath("$.balance_minor").value(0));
    }
}
