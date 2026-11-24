package com.kyc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.entities.CreditAccount;
import com.kyc.entities.Enrollment;
import com.kyc.enums.EnrollmentSource;
import com.kyc.ports.MailPort;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.EnrollmentRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
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
class ConsoleAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Autowired
    private CreditAccountRepository creditAccounts;

    @Autowired
    private EnrollmentRepository enrollments;

    @Test
    void consoleCreatesLinkAndListsIt() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "auth-console@example.com", "Auth Console");
        setBalance(owner, 5000);
        MvcResult createdIntegration = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"live\",\"name\":\"Auth\",\"product\":\"authentication\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("live"))
                .andReturn();
        String integrationId = IdvSupport.JSON.readTree(createdIntegration.getResponse().getContentAsString())
                .get("id")
                .asText();
        UUID orgId = AccountSupport.organizationId(mockMvc, owner);
        enrollments.save(new Enrollment(
                UUID.randomUUID(),
                orgId,
                "user-1",
                EnrollmentSource.IDV,
                UUID.randomUUID(),
                "face-console",
                "rm-auth-console",
                Instant.parse("2026-11-22T18:00:00Z")));

        MvcResult created = mockMvc.perform(post("/v1/console/authentications")
                        .cookie(owner)
                        .header("Idempotency-Key", "console-auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"external_id":"user-1","integration_id":"%s"}
                                """
                                .formatted(integrationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.external_id").value("user-1"))
                .andExpect(jsonPath("$.engine").value("auth-2"))
                .andExpect(jsonPath("$.hosted_url").value(startsWith("http")))
                .andReturn();
        String id = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/v1/console/authentications").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].integration_mode").value("live"))
                .andExpect(jsonPath("$.data[0].hosted_url").doesNotExist());

        mockMvc.perform(get("/v1/console/authentications/" + id).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosted_url").value(startsWith("http")));

        Cookie other = AccountSupport.signupVerified(mockMvc, mailPort, "auth-console-b@example.com", "Auth Other");
        mockMvc.perform(get("/v1/console/authentications/" + id).cookie(other)).andExpect(status().isNotFound());
    }

    @Test
    void missingEnrollmentDoesNotCreate() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "auth-none@example.com", "Auth None");
        setBalance(owner, 5000);
        MvcResult createdIntegration = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"live\",\"name\":\"Auth\",\"product\":\"authentication\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String integrationId = IdvSupport.JSON.readTree(createdIntegration.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/v1/console/authentications")
                        .cookie(owner)
                        .header("Idempotency-Key", "console-auth-missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"external_id":"missing","integration_id":"%s"}
                                """
                                .formatted(integrationId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("no_enrollment"));
    }

    private void setBalance(Cookie owner, long balance) throws Exception {
        CreditAccount account = creditAccounts.findById(AccountSupport.organizationId(mockMvc, owner)).orElseThrow();
        account.apply(balance - account.getBalanceMinor(), Instant.now());
        creditAccounts.saveAndFlush(account);
    }
}
