package com.kyc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.MailPort;
import com.kyc.repositories.CreditAccountRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillingIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private CreditAccountRepository creditAccounts;

    @Test
    void ownerSeesZeroBalanceAndMemberIsForbidden() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "bill-owner@example.com", "Bill Co");

        mockMvc.perform(get("/v1/console/billing").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("usd"))
                .andExpect(jsonPath("$.balance_minor").value(0))
                .andExpect(jsonPath("$.unit_amount_minor").value(900))
                .andExpect(jsonPath("$.live_unlocked").value(false))
                .andExpect(jsonPath("$.packs", hasSize(4)))
                .andExpect(jsonPath("$.packs[0]").value(5000))
                .andExpect(jsonPath("$.ledger.entries", hasSize(0)));

        mockMvc.perform(get("/v1/console/me").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance_minor").value(0))
                .andExpect(jsonPath("$.live_unlocked").value(false));

        UUID orgId = UUID.fromString(AccountSupport.me(mockMvc, owner).get("organization").get("id").asText());
        org.junit.jupiter.api.Assertions.assertTrue(creditAccounts.findById(orgId).isPresent());
        org.junit.jupiter.api.Assertions.assertEquals(1, creditAccounts.findAll().stream()
                .filter(row -> row.getOrganizationId().equals(orgId))
                .count());

        AccountSupport.invite(mockMvc, owner, "bill-admin@example.com", "admin");
        Cookie admin = AccountSupport.acceptInvite(mockMvc, mailPort, "bill-admin@example.com");
        mockMvc.perform(get("/v1/console/billing").cookie(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));

        AccountSupport.invite(mockMvc, owner, "bill-member@example.com", "member");
        Cookie member = AccountSupport.acceptInvite(mockMvc, mailPort, "bill-member@example.com");
        mockMvc.perform(get("/v1/console/billing").cookie(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
        mockMvc.perform(post("/v1/console/billing/checkout")
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pack_minor\":5000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherOrganizationDoesNotSeeBalance() throws Exception {
        Cookie ownerA = AccountSupport.signupVerified(mockMvc, mailPort, "bill-a@example.com", "Bill A");
        Cookie ownerB = AccountSupport.signupVerified(mockMvc, mailPort, "bill-b@example.com", "Bill B");
        String idA = AccountSupport.me(mockMvc, ownerA).get("organization").get("id").asText();
        String idB = AccountSupport.me(mockMvc, ownerB).get("organization").get("id").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals(idA, idB);
        mockMvc.perform(get("/v1/console/billing").cookie(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance_minor").value(0));
        mockMvc.perform(get("/v1/console/billing").cookie(ownerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance_minor").value(0));
    }
}
