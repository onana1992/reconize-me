package com.kyc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.MailPort;
import jakarta.servlet.http.Cookie;
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
class ConsoleAuditTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void ownerReadsAuditWithoutPiiAndMemberIsForbidden() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-audit@example.com", "Audit Co");
        AccountSupport.invite(mockMvc, owner, "invitee-audit@example.com", "member");
        Cookie member = AccountSupport.acceptInvite(mockMvc, mailPort, "invitee-audit@example.com");

        MvcResult listed = mockMvc.perform(get("/v1/console/audit").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray())
                .andReturn();
        JsonNode body = JSON.readTree(listed.getResponse().getContentAsString());
        String raw = listed.getResponse().getContentAsString();
        assertFalse(raw.contains("invitee-audit@example.com"));
        assertFalse(raw.contains("owner-audit@example.com"));
        assertTrue(actions(body).contains("membership.invited"));

        mockMvc.perform(get("/v1/console/audit").cookie(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void auditFilterAndCursorAndRevokedKeyAndLoginFailed() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-audit-f@example.com", "Audit Filter");
        AccountSupport.createIntegration(mockMvc, owner, "Audit keys");
        AccountSupport.invite(mockMvc, owner, "member-audit-f@example.com", "member");
        AccountSupport.acceptInvite(mockMvc, mailPort, "member-audit-f@example.com");

        MvcResult keys = mockMvc.perform(get("/v1/console/api-keys").cookie(owner))
                .andExpect(status().isOk())
                .andReturn();
        String keyId = JSON.readTree(keys.getResponse().getContentAsString()).get(0).get("id").asText();
        mockMvc.perform(post("/v1/console/api-keys/" + keyId + "/revoke")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .cookie(owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner-audit-f@example.com","password":"WrongPass12!x"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/console/audit").param("action", "api_key.revoked").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].action").value("api_key.revoked"))
                .andExpect(jsonPath("$.events[0].ip_address").value("203.0.113.10"))
                .andExpect(jsonPath("$.events[0].payload").exists());

        mockMvc.perform(get("/v1/console/audit").param("action", "user.login_failed").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].action").value("user.login_failed"));

        MvcResult page1 = mockMvc.perform(get("/v1/console/audit").param("limit", "1").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next_cursor").isString())
                .andReturn();
        JsonNode first = JSON.readTree(page1.getResponse().getContentAsString());
        String cursor = first.get("next_cursor").asText();
        MvcResult page2 = mockMvc.perform(get("/v1/console/audit")
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .cookie(owner))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode second = JSON.readTree(page2.getResponse().getContentAsString());
        assertNotEquals(first.get("events").get(0).get("id").asLong(), second.get("events").get(0).get("id").asLong());
    }

    @Test
    void cannotReadAnotherOrganizationAudit() throws Exception {
        Cookie a = AccountSupport.signupVerified(mockMvc, mailPort, "owner-audit-a@example.com", "Audit A");
        Cookie b = AccountSupport.signupVerified(mockMvc, mailPort, "owner-audit-b@example.com", "Audit B");
        AccountSupport.invite(mockMvc, b, "secret-b@example.com", "member");

        MvcResult listed = mockMvc.perform(get("/v1/console/audit").param("action", "membership.invited").cookie(a))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode events = JSON.readTree(listed.getResponse().getContentAsString()).get("events");
        assertTrue(events.isEmpty() || events.isNull() || events.size() == 0);
    }

    private static java.util.Set<String> actions(JsonNode body) {
        java.util.Set<String> actions = new java.util.HashSet<>();
        for (JsonNode event : body.get("events")) {
            actions.add(event.get("action").asText());
        }
        return actions;
    }
}
