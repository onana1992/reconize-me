package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsoleRequiresSessionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/console/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }
}
