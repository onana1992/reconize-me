package com.kyc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.MailPort;
import com.kyc.services.SessionService;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class AccountSupport {

    static final String PASSWORD = "Password12!x";
    private static final ObjectMapper JSON = new ObjectMapper();

    private AccountSupport() {}

    static String userId(MvcResult signup) throws Exception {
        return field(signup, "user_id");
    }

    static String field(MvcResult result, String name) throws Exception {
        JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
        return node.get(name).asText();
    }

    static String tokenFromMail(MailPort mail, String userId, String queryKey) {
        String url = mail.lastUrl(userId).orElseThrow(() -> new IllegalStateException("No mail for " + userId));
        return query(url, queryKey);
    }

    static String tokenFromTemplate(MailPort mail, String template, String queryKey) {
        String url = mail.lastUrlForTemplate(template)
                .orElseThrow(() -> new IllegalStateException("No mail for template " + template));
        return query(url, queryKey);
    }

    static Cookie session(MvcResult login) {
        Cookie cookie = login.getResponse().getCookie(SessionService.COOKIE_NAME);
        if (cookie == null) {
            throw new IllegalStateException("Missing rm_session cookie");
        }
        return cookie;
    }

    static Cookie signupVerified(MockMvc mockMvc, MailPort mailPort, String email, String org) throws Exception {
        MvcResult signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","organization_name":"%s"}
                                """
                                .formatted(email, PASSWORD, org)))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(get("/v1/account/verify")
                        .param("token", tokenFromMail(mailPort, userId(signup), "token=")))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """
                                .formatted(email, PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return session(login);
    }

    static JsonNode me(MockMvc mockMvc, Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/console/me").cookie(cookie)).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    static JsonNode team(MockMvc mockMvc, Cookie cookie) throws Exception {
        MvcResult result =
                mockMvc.perform(get("/v1/console/team").cookie(cookie)).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    static void invite(MockMvc mockMvc, Cookie cookie, String email) throws Exception {
        invite(mockMvc, cookie, email, null);
    }

    static void invite(MockMvc mockMvc, Cookie cookie, String email, String role) throws Exception {
        String body = role == null
                ? """
                        {"email":"%s"}
                        """
                        .formatted(email)
                : """
                        {"email":"%s","role":"%s"}
                        """
                        .formatted(email, role);
        mockMvc.perform(post("/v1/console/team/invites")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    static Cookie acceptInvite(MockMvc mockMvc, MailPort mailPort, String email) throws Exception {
        String invite = tokenFromTemplate(mailPort, "team_invite", "invite=");
        MvcResult signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","organization_name":"Ignored","invite_token":"%s"}
                                """
                                .formatted(email, PASSWORD, invite)))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(get("/v1/account/verify")
                        .param("token", tokenFromMail(mailPort, userId(signup), "token=")))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """
                                .formatted(email, PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return session(login);
    }

    static String memberIdByEmail(JsonNode team, String email) {
        for (JsonNode member : team.get("members")) {
            if (email.equals(member.get("email").asText())) {
                return member.get("id").asText();
            }
        }
        throw new IllegalStateException("Member not listed: " + email);
    }

    private static String query(String url, String key) {
        int index = url.indexOf(key);
        if (index < 0) {
            throw new IllegalStateException("Query " + key + " missing in " + url);
        }
        String value = url.substring(index + key.length());
        int amp = value.indexOf('&');
        return amp < 0 ? value : value.substring(0, amp);
    }
}
