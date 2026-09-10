package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.OrganizationRepository;
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
class DocumentCaptureTest {

    private static final String KEY = "ky_test_doc00001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Test
    void uploadWithoutConsentIsConflict() throws Exception {
        IdvSupport.seedBearer(organizations, apiKeys, passwordEncoder, KEY, "Doc Co", "doc-co");
        String token = IdvSupport.token(IdvSupport.create(mockMvc, KEY, "{}"));
        mockMvc.perform(post("/v1/flow/" + token + "/document/uploads"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("invalid_status"));
    }

    @Test
    void acceptedDocumentThenQualityFailThenCap() throws Exception {
        IdvSupport.seedBearer(organizations, apiKeys, passwordEncoder, KEY, "Doc Co", "doc-co");
        String token = IdvSupport.token(IdvSupport.create(mockMvc, KEY, "{}"));
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);

        var ok = IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        org.junit.jupiter.api.Assertions.assertTrue(ok.path("accepted").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("capture_selfie", ok.path("next").asText());
    }

    @Test
    void qualityRejectedThenThreeFailuresDecline() throws Exception {
        IdvSupport.seedBearer(organizations, apiKeys, passwordEncoder, KEY, "Doc Co", "doc-co");
        String token = IdvSupport.token(IdvSupport.create(mockMvc, KEY, "{}"));
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);

        byte[] tiny = IdvSupport.tinyJpeg();
        var first = IdvSupport.captureDocument(mockMvc, token, tiny);
        org.junit.jupiter.api.Assertions.assertFalse(first.path("accepted").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("recapture_requested", first.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("capture_document", first.path("next").asText());

        var second = IdvSupport.captureDocument(mockMvc, token, tiny);
        org.junit.jupiter.api.Assertions.assertFalse(second.path("accepted").asBoolean());

        var third = IdvSupport.captureDocument(mockMvc, token, tiny);
        org.junit.jupiter.api.Assertions.assertFalse(third.path("accepted").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("declined", third.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("done", third.path("next").asText());
    }

    @Test
    void completeWithoutObjectKeepsPending() throws Exception {
        IdvSupport.seedBearer(organizations, apiKeys, passwordEncoder, KEY, "Doc Co", "doc-co");
        String token = IdvSupport.token(IdvSupport.create(mockMvc, KEY, "{}"));
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        mockMvc.perform(post("/v1/flow/" + token + "/document/uploads")).andExpect(status().isOk());
        mockMvc.perform(post("/v1/flow/" + token + "/document/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attempt\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }
}
