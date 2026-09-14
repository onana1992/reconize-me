package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.MailPort;
import jakarta.servlet.http.Cookie;
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
class TeamRolesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void readonlyCannotCreateVerification() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-ro@example.com", "Ro Co");
        Cookie readonly = inviteAndAccept(owner, "ro@example.com", "readonly");

        mockMvc.perform(post("/v1/console/verifications").cookie(readonly).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
        mockMvc.perform(get("/v1/console/verifications").cookie(readonly)).andExpect(status().isOk());
        mockMvc.perform(get("/v1/console/api-keys").cookie(readonly)).andExpect(status().isForbidden());
    }

    @Test
    void developerCannotReadVerificationsAndCanCreateKey() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-dev@example.com", "Dev Co");
        Cookie developer = inviteAndAccept(owner, "dev@example.com", "developer");

        mockMvc.perform(get("/v1/console/verifications").cookie(developer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
        mockMvc.perform(get("/v1/console/verifications/00000000-0000-0000-0000-000000000001").cookie(developer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(developer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"test\",\"name\":\"Dev keys\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").isString());
    }

    @Test
    void adminCanInviteAndMemberCannot() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-admin@example.com", "Admin Co");
        Cookie admin = inviteAndAccept(owner, "admin@example.com", "admin");
        Cookie member = inviteAndAccept(owner, "member-rbac@example.com", "member");

        mockMvc.perform(post("/v1/console/team/invites")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"from-admin@example.com","role":"member"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/console/team/invites")
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"from-member@example.com","role":"member"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void adminCannotPromoteToOwner() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-promo@example.com", "Promo Co");
        Cookie admin = inviteAndAccept(owner, "admin-promo@example.com", "admin");
        inviteAndAccept(owner, "member-promo@example.com", "member");
        String memberId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "member-promo@example.com");

        mockMvc.perform(patch("/v1/console/team/members/" + memberId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"owner"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void memberCannotChangeOwnRole() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-demote@example.com", "Demote Co");
        String ownerId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "owner-demote@example.com");

        mockMvc.perform(patch("/v1/console/team/members/" + ownerId)
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("cannot_change_own_role"));

        Cookie admin = inviteAndAccept(owner, "admin-self-role@example.com", "admin");
        String adminId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "admin-self-role@example.com");
        mockMvc.perform(patch("/v1/console/team/members/" + adminId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"member"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("cannot_change_own_role"));
    }

    @Test
    void memberCannotRemoveSelf() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-self-rm@example.com", "Self Rm Co");
        Cookie admin = inviteAndAccept(owner, "admin-self-rm@example.com", "admin");
        String adminId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "admin-self-rm@example.com");

        mockMvc.perform(delete("/v1/console/team/members/" + adminId).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("cannot_remove_self"));
    }

    @Test
    void roleChangeIsImmediateOnExistingSession() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-immediate@example.com", "Immediate Co");
        Cookie member = inviteAndAccept(owner, "member-immediate@example.com", "member");
        String memberId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "member-immediate@example.com");

        mockMvc.perform(get("/v1/console/me").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("member"));

        mockMvc.perform(patch("/v1/console/team/members/" + memberId)
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/console/me").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.permissions").isArray());
    }

    @Test
    void inviteRoleIsAppliedOnAccept() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-role-invite@example.com", "Role Invite");
        Cookie developer = inviteAndAccept(owner, "invited-dev@example.com", "developer");
        mockMvc.perform(get("/v1/console/me").cookie(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("developer"));
    }

    @Test
    void cannotInviteOwnerRole() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-no-owner@example.com", "No Owner Invite");
        mockMvc.perform(post("/v1/console/team/invites")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"would-be-owner@example.com","role":"owner"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void memberCannotListKeys() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-keys-403@example.com", "Keys 403");
        Cookie member = inviteAndAccept(owner, "member-keys-403@example.com", "member");
        mockMvc.perform(get("/v1/console/api-keys").cookie(member)).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanTransferThenFormerOwnerIsAdmin() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-xfer@example.com", "Xfer Co");
        Cookie member = inviteAndAccept(owner, "next-owner@example.com", "member");
        String memberId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "next-owner@example.com");

        mockMvc.perform(post("/v1/console/team/transfer")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user_id":"%s"}
                                """
                                .formatted(memberId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/console/me").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));
        mockMvc.perform(get("/v1/console/me").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("owner"));
    }

    private Cookie inviteAndAccept(Cookie owner, String email, String role) throws Exception {
        AccountSupport.invite(mockMvc, owner, email, role);
        return AccountSupport.acceptInvite(mockMvc, mailPort, email);
    }
}
