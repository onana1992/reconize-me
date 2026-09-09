package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class TeamLifecycleTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PASSWORD = "Password12!x";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void memberCannotMutateTeam() throws Exception {
        Owner owner = signupOwner("owner-team-403@example.com", "Team 403");
        invite(owner.cookie, "member-team-403@example.com");
        Cookie member = acceptInvite("member-team-403@example.com");
        invite(owner.cookie, "pending-team-403@example.com");
        JsonNode team = team(owner.cookie);
        String pendingId = team.get("invites").get(0).get("id").asText();

        mockMvc.perform(post("/v1/console/team/invites")
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"another-403@example.com"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));

        mockMvc.perform(post("/v1/console/team/invites/" + pendingId + "/resend").cookie(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));

        mockMvc.perform(delete("/v1/console/team/invites/" + pendingId).cookie(member))
                .andExpect(status().isForbidden());

        String ownerId = memberIdByEmail(team, "owner-team-403@example.com");
        mockMvc.perform(delete("/v1/console/team/members/" + ownerId).cookie(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void resendInvalidatesPreviousInviteToken() throws Exception {
        Owner owner = signupOwner("owner-resend@example.com", "Resend Co");
        invite(owner.cookie, "invitee-resend@example.com");
        String oldToken = AccountSupport.tokenFromTemplate(mailPort, "team_invite", "invite=");
        String inviteId = team(owner.cookie).get("invites").get(0).get("id").asText();

        mockMvc.perform(post("/v1/console/team/invites/" + inviteId + "/resend").cookie(owner.cookie))
                .andExpect(status().isNoContent());
        String newToken = AccountSupport.tokenFromTemplate(mailPort, "team_invite", "invite=");

        mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupInviteJson("invitee-resend@example.com", oldToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));

        mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupInviteJson("invitee-resend@example.com", newToken)))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelInviteRemovesItAndRejectsToken() throws Exception {
        Owner owner = signupOwner("owner-cancel@example.com", "Cancel Co");
        invite(owner.cookie, "invitee-cancel@example.com");
        String token = AccountSupport.tokenFromTemplate(mailPort, "team_invite", "invite=");
        String inviteId = team(owner.cookie).get("invites").get(0).get("id").asText();

        mockMvc.perform(delete("/v1/console/team/invites/" + inviteId).cookie(owner.cookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/console/team").cookie(owner.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invites.length()").value(0));

        mockMvc.perform(get("/v1/account/invites").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));
        mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupInviteJson("invitee-cancel@example.com", token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));
        mockMvc.perform(post("/v1/account/invites/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """
                                .formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));

        invite(owner.cookie, "invitee-cancel@example.com");
        mockMvc.perform(get("/v1/console/team").cookie(owner.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invites.length()").value(1));
        mockMvc.perform(get("/v1/account/invites").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));
        mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupInviteJson("invitee-cancel@example.com", token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));
    }

    @Test
    void cancelAfterSignupRejectsVerifyAndOldLink() throws Exception {
        Owner owner = signupOwner("owner-cancel-verify@example.com", "Cancel Verify Co");
        invite(owner.cookie, "invitee-cancel-verify@example.com");
        String inviteToken = AccountSupport.tokenFromTemplate(mailPort, "team_invite", "invite=");
        String inviteId = team(owner.cookie).get("invites").get(0).get("id").asText();

        mockMvc.perform(get("/v1/account/invites").param("token", inviteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("invitee-cancel-verify@example.com"));

        MvcResult signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupInviteJson("invitee-cancel-verify@example.com", inviteToken)))
                .andExpect(status().isCreated())
                .andReturn();
        String verifyToken =
                AccountSupport.tokenFromMail(mailPort, AccountSupport.userId(signup), "token=");

        mockMvc.perform(delete("/v1/console/team/invites/" + inviteId).cookie(owner.cookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/account/invites").param("token", inviteToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));
        mockMvc.perform(get("/v1/account/verify").param("token", verifyToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));
    }

    @Test
    void duplicatePendingInviteIsConflict() throws Exception {
        Owner owner = signupOwner("owner-dup@example.com", "Dup Co");
        invite(owner.cookie, "dup@example.com");
        mockMvc.perform(post("/v1/console/team/invites")
                        .cookie(owner.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("already_invited"));
    }

    @Test
    void removedMemberSessionIsRejected() throws Exception {
        Owner owner = signupOwner("owner-remove@example.com", "Remove Co");
        invite(owner.cookie, "member-remove@example.com");
        Cookie member = acceptInvite("member-remove@example.com");
        String memberId = memberIdByEmail(team(owner.cookie), "member-remove@example.com");

        mockMvc.perform(delete("/v1/console/team/members/" + memberId).cookie(owner.cookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/console/me").cookie(member)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/console/team").cookie(owner.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void ownerCannotRemoveSelf() throws Exception {
        Owner owner = signupOwner("owner-last@example.com", "Last Co");
        mockMvc.perform(delete("/v1/console/team/members/" + owner.userId).cookie(owner.cookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("cannot_remove_self"));
    }

    @Test
    void cannotMutateAnotherOrganization() throws Exception {
        Owner a = signupOwner("owner-iso-a@example.com", "Iso A Team");
        Owner b = signupOwner("owner-iso-b@example.com", "Iso B Team");
        invite(b.cookie, "member-iso-b@example.com");
        String inviteId = team(b.cookie).get("invites").get(0).get("id").asText();

        mockMvc.perform(post("/v1/console/team/invites/" + inviteId + "/resend").cookie(a.cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/console/team/invites/" + inviteId).cookie(a.cookie))
                .andExpect(status().isNotFound());

        Cookie member = acceptInvite("member-iso-b@example.com");
        String memberId = memberIdByEmail(team(b.cookie), "member-iso-b@example.com");
        mockMvc.perform(delete("/v1/console/team/members/" + memberId).cookie(a.cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/console/me").cookie(member)).andExpect(status().isOk());
    }

    private Owner signupOwner(String email, String org) throws Exception {
        MvcResult signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","organization_name":"%s"}
                                """
                                .formatted(email, PASSWORD, org)))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = AccountSupport.userId(signup);
        mockMvc.perform(get("/v1/account/verify")
                        .param("token", AccountSupport.tokenFromMail(mailPort, userId, "token=")))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """
                                .formatted(email, PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return new Owner(userId, AccountSupport.session(login));
    }

    private void invite(Cookie cookie, String email) throws Exception {
        mockMvc.perform(post("/v1/console/team/invites")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """
                                .formatted(email)))
                .andExpect(status().isCreated());
    }

    private Cookie acceptInvite(String email) throws Exception {
        String invite = AccountSupport.tokenFromTemplate(mailPort, "team_invite", "invite=");
        MvcResult signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupInviteJson(email, invite)))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(get("/v1/account/verify")
                        .param(
                                "token",
                                AccountSupport.tokenFromMail(mailPort, AccountSupport.userId(signup), "token=")))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """
                                .formatted(email, PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return AccountSupport.session(login);
    }

    private JsonNode team(Cookie cookie) throws Exception {
        MvcResult result =
                mockMvc.perform(get("/v1/console/team").cookie(cookie)).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static String memberIdByEmail(JsonNode team, String email) {
        for (JsonNode member : team.get("members")) {
            if (email.equals(member.get("email").asText())) {
                return member.get("id").asText();
            }
        }
        throw new IllegalStateException("Member not listed: " + email);
    }

    private static String signupInviteJson(String email, String token) {
        return """
                {"email":"%s","password":"%s","organization_name":"Ignored","invite_token":"%s"}
                """
                .formatted(email, PASSWORD, token);
    }

    private record Owner(String userId, Cookie cookie) {}
}
