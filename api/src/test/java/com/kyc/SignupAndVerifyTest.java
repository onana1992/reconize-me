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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignupAndVerifyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void signupVerifyDoesNotIssueKey() throws Exception {
        MvcResult signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@example.com","password":"Password12!x","organization_name":"Acme"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").exists())
                .andReturn();

        String userId = AccountSupport.userId(signup);
        String token = AccountSupport.tokenFromMail(mailPort, userId, "token=");

        mockMvc.perform(get("/v1/account/verify").param("token", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").doesNotExist());

        mockMvc.perform(get("/v1/account/verify").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_or_expired_token"));
    }
}
