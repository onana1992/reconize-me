package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.ApiKey;
import com.kyc.entities.Organization;
import com.kyc.ports.MailPort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiKeyStillBearerTest {

    private static final String SEED = "ky_test_bearer01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    void cookieDoesNotOpenVerificationsApi() throws Exception {
        var signup = mockMvc.perform(post("/v1/account/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie@example.com","password":"Password12!x","organization_name":"Cookie Org"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String token = AccountSupport.tokenFromMail(mailPort, AccountSupport.userId(signup), "token=");
        mockMvc.perform(get("/v1/account/verify").param("token", token)).andExpect(status().isCreated());
        var login = mockMvc.perform(post("/v1/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie@example.com","password":"Password12!x"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();

        mockMvc.perform(get("/v1/verifications").cookie(AccountSupport.session(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));

        Instant now = Instant.parse("2026-08-26T12:00:00Z");
        UUID orgId = UUID.fromString("018f0000-0000-7000-8000-0000000000d1");
        organizationRepository.save(new Organization(orgId, "Seed", "seed-bearer", now));
        apiKeyRepository.save(new ApiKey(
                UUID.fromString("018f0000-0000-7000-8000-0000000000d2"),
                orgId,
                SEED.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(SEED),
                now));

        mockMvc.perform(get("/v1/verifications").header("Authorization", "Bearer " + SEED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
