package com.kyc;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.StripeCustomer;
import com.kyc.enums.ProductCode;
import com.kyc.ports.MailPort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.StripeCustomerRepository;
import com.kyc.services.auth.AuthPolicy;
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
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private StripeCustomerRepository stripeCustomers;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Test
    void authenticationTestModeIsInvalidAndCreatesNothing() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "auth-test@example.com", "Auth Test");
        credit(owner, 5000);

        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"authentication\",\"mode\":\"test\",\"name\":\"Auth Sandbox\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid"));

        UUID orgId = AccountSupport.organizationId(mockMvc, owner);
        assertTrue(integrations.findByOrganizationIdOrderByCreatedAtAsc(orgId).stream()
                .noneMatch(row -> row.getProduct() == ProductCode.AUTHENTICATION));
        assertFalse(apiKeys.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .anyMatch(key -> key.getKeyPrefix().startsWith("ky_test_")
                        && integrations.findById(key.getIntegrationId())
                                .map(i -> i.getProduct() == ProductCode.AUTHENTICATION)
                                .orElse(false)));
    }

    @Test
    void authenticationLiveIssuesKyLiveOnlyAndRejectsOtherProductRoutes() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "auth-live@example.com", "Auth Live");
        credit(owner, 5000);

        MvcResult created = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"authentication\",\"mode\":\"live\",\"name\":\"Auth Production\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product").value("authentication"))
                .andExpect(jsonPath("$.mode").value("live"))
                .andExpect(jsonPath("$.key").value(startsWith("ky_live_")))
                .andReturn();
        String key = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("key").asText();
        assertFalse(key.startsWith("ky_test_"));

        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authentication_amount_minor").value(10))
                .andExpect(jsonPath("$.usage[2].product").value("authentication"))
                .andExpect(jsonPath("$.usage[2].live_count").value(0));

        mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));

        mockMvc.perform(get("/v1/screenings").header("Authorization", "Bearer " + key))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void authPolicy0FactoryMatchesContract() {
        AuthPolicy policy = AuthPolicy.factory();
        assertEquals("auth-policy-0", policy.version());
        assertEquals(AuthPolicy.FLOOR_APPROVAL, policy.approvalThreshold());
        assertEquals(AuthPolicy.FLOOR_REVIEW, policy.reviewThreshold());
        assertEquals(3, policy.maxCaptureAttempts());
        assertTrue(policy.livenessRequired());
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
