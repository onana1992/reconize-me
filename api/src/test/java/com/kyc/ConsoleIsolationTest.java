package com.kyc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.MailPort;
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
class ConsoleIsolationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void sessionSeesOwnOrganizationOnly() throws Exception {
        var a = session("iso-a@example.com", "Iso A");
        var b = session("iso-b@example.com", "Iso B");

        String orgA = orgId(a);
        String orgB = orgId(b);
        assertNotEquals(orgA, orgB);

        var created = IdvSupport.createConsole(mockMvc, a, "{}");
        String id = created.path("id").asText();
        mockMvc.perform(get("/v1/console/verifications/" + id).cookie(a)).andExpect(status().isOk());
        mockMvc.perform(get("/v1/console/verifications/" + id).cookie(b)).andExpect(status().isNotFound());
    }

    private String orgId(jakarta.servlet.http.Cookie cookie) throws Exception {
        var result = mockMvc.perform(get("/v1/console/me").cookie(cookie)).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString())
                .path("organization")
                .path("id")
                .asText();
    }

    private jakarta.servlet.http.Cookie session(String email, String org) throws Exception {
        var signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password12!x","organization_name":"%s"}
                                """
                                .formatted(email, org)))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(get("/v1/account/verify")
                        .param("token", AccountSupport.tokenFromMail(mailPort, AccountSupport.userId(signup), "token=")))
                .andExpect(status().isCreated());
        var login = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password12!x"}
                                """
                                .formatted(email)))
                .andExpect(status().isNoContent())
                .andReturn();
        return AccountSupport.session(login);
    }
}
