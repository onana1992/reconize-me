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
class ApiKeySecretNotRelistedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void listKeysOmitsSecret() throws Exception {
        var signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"keys@example.com","password":"Password12!x","organization_name":"Keys"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String token = AccountSupport.tokenFromMail(mailPort, AccountSupport.userId(signup), "token=");
        mockMvc.perform(get("/v1/account/verify").param("token", token)).andExpect(status().isCreated());
        var login = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"keys@example.com","password":"Password12!x"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie session = AccountSupport.session(login);
        AccountSupport.createIntegration(mockMvc, session, "Keys");

        mockMvc.perform(get("/v1/console/api-keys").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].key_prefix").exists())
                .andExpect(jsonPath("$[0].key").doesNotExist())
                .andExpect(jsonPath("$[0].key_hash").doesNotExist());
    }
}
