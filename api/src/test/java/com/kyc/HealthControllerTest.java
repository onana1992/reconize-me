package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsOk() throws Exception {
        mockMvc.perform(get("/v1/health").header("X-Request-Id", "req_health_ok"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req_health_ok"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("recogniz-me"));
    }
}
