package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.ports.MailPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "kyc.auth-rate-limit=3")
class LoginRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void successfulLoginsDoNotTripTheLimit() throws Exception {
        AccountSupport.signupVerified(mockMvc, mailPort, "ok@example.com", "Ok");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/v1/account/login")
                            .header("X-Forwarded-For", "203.0.113.10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ok@example.com","password":"%s"}
                                    """
                                    .formatted(AccountSupport.PASSWORD)))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void onlyFailedLoginsCountTowardTheLimit() throws Exception {
        AccountSupport.signupVerified(mockMvc, mailPort, "fail@example.com", "Fail");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/v1/account/login")
                            .header("X-Forwarded-For", "203.0.113.11")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"fail@example.com","password":"WrongPassword1!"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/v1/account/login")
                        .header("X-Forwarded-For", "203.0.113.11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"fail@example.com","password":"%s"}
                                """
                                .formatted(AccountSupport.PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("rate_limited"));
    }
}
