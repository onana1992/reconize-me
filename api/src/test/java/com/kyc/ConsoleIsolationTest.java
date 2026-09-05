package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void sessionACannotReadOrgBVerification() throws Exception {
        var a = session("iso-a@example.com", "Iso A");
        var created = mockMvc.perform(post("/v1/console/verifications")
                        .cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String verificationId = AccountSupport.field(created, "id");

        var b = session("iso-b@example.com", "Iso B");
        mockMvc.perform(get("/v1/console/verifications/" + verificationId).cookie(b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    private jakarta.servlet.http.Cookie session(String email, String org) throws Exception {
        var signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password12","organization_name":"%s"}
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
                                {"email":"%s","password":"password12"}
                                """
                                .formatted(email)))
                .andExpect(status().isNoContent())
                .andReturn();
        return AccountSupport.session(login);
    }
}
