package com.kyc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntegrationLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailPort mailPort;

    @Test
    void namedIntegrationIssuesOneKeyAndLiveIsLocked() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "int-owner@example.com", "Int Co");

        mockMvc.perform(get("/v1/console/integrations").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));

        MvcResult created = mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"test\",\"name\":\"Staging\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("test"))
                .andExpect(jsonPath("$.name").value("Staging"))
                .andExpect(jsonPath("$.key").value(startsWith("ky_test_")))
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andReturn();
        String integrationId = IdvSupport.JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"test\",\"name\":\"Staging\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("name_taken"));

        mockMvc.perform(get("/v1/console/integrations/" + integrationId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].key_prefix").exists())
                .andExpect(jsonPath("$.key").doesNotExist());

        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"live\",\"name\":\"Production\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("live_locked"));

        mockMvc.perform(post("/v1/console/integrations/" + integrationId + "/api-keys").cookie(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("key_exists"));

        mockMvc.perform(post("/v1/console/verifications")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));

        mockMvc.perform(post("/v1/console/verifications")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"integration_id\":\"" + integrationId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.integration_id").value(integrationId))
                .andExpect(jsonPath("$.integration_mode").value("test"));
    }

    @Test
    void otherOrganizationSeesNotFound() throws Exception {
        Cookie ownerA = AccountSupport.signupVerified(mockMvc, mailPort, "int-a@example.com", "Int A");
        Cookie ownerB = AccountSupport.signupVerified(mockMvc, mailPort, "int-b@example.com", "Int B");
        String idA = AccountSupport.createIntegration(mockMvc, ownerA, "Alpha").get("id").asText();

        mockMvc.perform(get("/v1/console/integrations/" + idA).cookie(ownerB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/console/integrations/" + idA + "/api-keys").cookie(ownerB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/console/verifications")
                        .cookie(ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"integration_id\":\"" + idA + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberCannotCreateIntegration() throws Exception {
        Cookie owner = AccountSupport.signupVerified(mockMvc, mailPort, "int-own2@example.com", "Int Own");
        AccountSupport.invite(mockMvc, owner, "int-member@example.com", "member");
        Cookie member = AccountSupport.acceptInvite(mockMvc, mailPort, "int-member@example.com");

        mockMvc.perform(get("/v1/console/integrations").cookie(member)).andExpect(status().isOk());
        mockMvc.perform(post("/v1/console/integrations")
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"test\",\"name\":\"Member\"}"))
                .andExpect(status().isForbidden());
    }
}
