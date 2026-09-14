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
class MemberCannotRevokeKeyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void memberRevokeIsForbidden() throws Exception {
        var ownerSignup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner-revoke@example.com","password":"Password12!x","organization_name":"Revoke Co"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(get("/v1/account/verify")
                        .param(
                                "token",
                                AccountSupport.tokenFromMail(mailPort, AccountSupport.userId(ownerSignup), "token=")))
                .andExpect(status().isCreated());
        var ownerLogin = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner-revoke@example.com","password":"Password12!x"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie ownerSession = AccountSupport.session(ownerLogin);
        AccountSupport.createIntegration(mockMvc, ownerSession, "Revoke");

        var keys = mockMvc.perform(get("/v1/console/api-keys").cookie(ownerSession))
                .andExpect(status().isOk())
                .andReturn();
        String firstId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(keys.getResponse().getContentAsString())
                .get(0)
                .get("id")
                .asText();

        mockMvc.perform(post("/v1/console/team/invites")
                        .cookie(AccountSupport.session(ownerLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member-revoke@example.com"}
                                """))
                .andExpect(status().isCreated());

        String invite = AccountSupport.tokenFromTemplate(mailPort, "team_invite", "invite=");
        var memberSignup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member-revoke@example.com","password":"Password12!x","organization_name":"Ignored","invite_token":"%s"}
                                """
                                .formatted(invite)))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(get("/v1/account/verify")
                        .param(
                                "token",
                                AccountSupport.tokenFromMail(mailPort, AccountSupport.userId(memberSignup), "token=")))
                .andExpect(status().isCreated());
        var memberLogin = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member-revoke@example.com","password":"Password12!x"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();

        mockMvc.perform(post("/v1/console/api-keys/" + firstId + "/revoke")
                        .cookie(AccountSupport.session(memberLogin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }
}
