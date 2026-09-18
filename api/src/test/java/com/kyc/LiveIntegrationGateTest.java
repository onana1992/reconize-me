package com.kyc;

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
class LiveIntegrationGateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private StripeCustomerRepository stripeCustomers;

    @Test
    void liveIsForbiddenUntilCreditThenIssuesKyLiveOnce() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "live-gate@example.com", "Live Gate");

        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"live\",\"name\":\"Production\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("insufficient_credit"));

        credit(owner, 5000);

        MvcResult created = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"live\",\"name\":\"Production\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("live"))
                .andExpect(jsonPath("$.key").value(startsWith("ky_live_")))
                .andReturn();
        String id = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/v1/console/integrations/" + id).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").doesNotExist())
                .andExpect(jsonPath("$.keys[0].key_prefix").value(startsWith("ky_live_")));

        Cookie ownerB = AccountSupport.signupVerified(mockMvc, mailPort, "live-gate-b@example.com", "Live Gate B");
        mockMvc.perform(get("/v1/console/integrations/" + id).cookie(ownerB)).andExpect(status().isNotFound());

        AccountSupport.invite(mockMvc, owner, "live-gate-member@example.com", "member");
        Cookie member = AccountSupport.acceptInvite(mockMvc, mailPort, "live-gate-member@example.com");
        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"live\",\"name\":\"Member Live\"}"))
                .andExpect(status().isForbidden());
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
