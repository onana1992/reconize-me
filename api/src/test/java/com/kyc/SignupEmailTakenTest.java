package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SignupEmailTakenTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String body =
                """
                {"email":"taken@example.com","password":"password12","organization_name":"First"}
                """;
        mockMvc.perform(post("/v1/account/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"TAKEN@example.com","password":"password12","organization_name":"Second"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("email_taken"));
    }
}
