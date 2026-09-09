package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class TeamStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void disableLogsMemberOutImmediately() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-disable@example.com", "Disable Co");
        AccountSupport.invite(mockMvc, owner, "member-disable@example.com", "member");
        Cookie member = AccountSupport.acceptInvite(mockMvc, mailPort, "member-disable@example.com");
        String memberId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "member-disable@example.com");

        mockMvc.perform(post("/v1/console/team/members/" + memberId + "/disable").cookie(owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/console/me").cookie(member)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/console/team").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.email=='member-disable@example.com')].status").value("disabled"));
    }

    @Test
    void disabledMemberCannotLoginUntilEnabled() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-reenable@example.com", "Enable Co");
        AccountSupport.invite(mockMvc, owner, "member-reenable@example.com", "member");
        AccountSupport.acceptInvite(mockMvc, mailPort, "member-reenable@example.com");
        String memberId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "member-reenable@example.com");

        mockMvc.perform(post("/v1/console/team/members/" + memberId + "/disable").cookie(owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member-reenable@example.com","password":"%s"}
                                """
                                .formatted(AccountSupport.PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("membership_disabled"));

        mockMvc.perform(post("/v1/console/team/members/" + memberId + "/enable").cookie(owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member-reenable@example.com","password":"%s"}
                                """
                                .formatted(AccountSupport.PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    void cannotDisableSelfOrLastOwner() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-self-off@example.com", "Self Off");
        String ownerId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "owner-self-off@example.com");

        mockMvc.perform(post("/v1/console/team/members/" + ownerId + "/disable").cookie(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("cannot_disable_self"));
    }

    @Test
    void lastOwnerCannotBeDisabledByAnotherOwner() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-last-off@example.com", "Last Off");
        AccountSupport.invite(mockMvc, owner, "second-off@example.com", "admin");
        Cookie admin = AccountSupport.acceptInvite(mockMvc, mailPort, "second-off@example.com");
        String ownerId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "owner-last-off@example.com");

        mockMvc.perform(post("/v1/console/team/members/" + ownerId + "/disable").cookie(admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/console/team/members/" + ownerId + "/disable").cookie(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("cannot_disable_self"));
    }

    @Test
    void adminCanDisableMember() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "owner-admin-off@example.com", "Admin Off");
        AccountSupport.invite(mockMvc, owner, "admin-off@example.com", "admin");
        Cookie admin = AccountSupport.acceptInvite(mockMvc, mailPort, "admin-off@example.com");
        AccountSupport.invite(mockMvc, owner, "target-off@example.com", "member");
        AccountSupport.acceptInvite(mockMvc, mailPort, "target-off@example.com");
        String targetId = AccountSupport.memberIdByEmail(
                AccountSupport.team(mockMvc, owner), "target-off@example.com");

        mockMvc.perform(post("/v1/console/team/members/" + targetId + "/disable").cookie(admin))
                .andExpect(status().isNoContent());
    }
}
