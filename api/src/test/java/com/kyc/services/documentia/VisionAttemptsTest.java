package com.kyc.services.documentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.dto.documentia.DocumentIaCatalogResponse.Schema;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.ports.VisionDocumentPort;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VisionAttemptsTest {

    private static final UUID VERSION = UUID.fromString("018f5a00-0000-7000-8000-0000000000a2");

    private final DocumentAnalysisParser parser = new DocumentAnalysisParser(new ObjectMapper(), code -> {
        if (!"QUEBEC_DRIVER_LICENSE".equals(code)) {
            return List.of();
        }
        return List.of(new ActiveSchema(
                VERSION,
                "QUEBEC_DRIVER_LICENSE",
                "CA",
                "DRIVING_LICENSE",
                "2024",
                List.of("FRONT"),
                "QC",
                Set.of("lastName")));
    });

    @Test
    void promptListsEveryActiveSchema() {
        String prompt = VisionPrompt.fromCatalog(List.of(
                new Schema(
                        VERSION,
                        "QUEBEC_DRIVER_LICENSE",
                        "CA",
                        "DRIVING_LICENSE",
                        "FRONT",
                        "2024",
                        "QC",
                        List.of(new com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt(
                                "expirationDate", true, "Expire le"))),
                new Schema(
                        UUID.randomUUID(),
                        "CAMEROON_NATIONAL_ID",
                        "CM",
                        "NATIONAL_ID",
                        "FRONT",
                        "2016",
                        null,
                        List.of())));
        assertTrue(prompt.contains("QUEBEC_DRIVER_LICENSE"));
        assertTrue(prompt.contains("CAMEROON_NATIONAL_ID"));
        assertTrue(prompt.contains("2016"));
        assertTrue(prompt.contains("expirationDate"));
        assertTrue(prompt.contains("Expire le"));
        assertTrue(VisionPrompt.fromCatalog(List.of(), "BACK").contains("Capture side: BACK"));
    }

    @Test
    void secondAttemptIsUsedWhenTheFirstFails() {
        AtomicInteger calls = new AtomicInteger();
        VisionDocumentPort port = (image, mediaType, prompt) -> {
            if (calls.incrementAndGet() == 1) {
                throw new ProviderUnavailableException("vision_unavailable");
            }
            return """
                    { "detection": "DOCUMENT_PRESENT", "classification": { "code": "QUEBEC_DRIVER_LICENSE", "version": "2024", "confidence": 0.94 } }
                    """;
        };
        VisionAttempts.Result result = VisionAttempts.call(port, parser, new byte[] {1}, "image/png", "catalog");
        assertEquals(2, calls.get());
        assertEquals("QUEBEC_DRIVER_LICENSE", result.success().parsed().document().classification().code());
    }

    @Test
    void twoFailuresReturnNoJson() {
        AtomicInteger calls = new AtomicInteger();
        VisionDocumentPort port = (image, mediaType, prompt) -> {
            calls.incrementAndGet();
            throw new ProviderUnavailableException("vision_unavailable");
        };
        VisionAttempts.Result result = VisionAttempts.call(port, parser, new byte[] {1}, "image/png", "catalog");
        assertNull(result.success());
        assertEquals("provider_unavailable", result.failureCode());
        assertEquals(2, calls.get());
    }

    @Test
    void rejectedJsonIsRetriedThenDropped() {
        AtomicInteger calls = new AtomicInteger();
        VisionDocumentPort port = (image, mediaType, prompt) -> {
            calls.incrementAndGet();
            return """
                    { "detection": "SELFIE" }
                    """;
        };
        VisionAttempts.Result result = VisionAttempts.call(port, parser, new byte[] {1}, "image/png", "catalog");
        assertNull(result.success());
        assertEquals("invalid_model_json", result.failureCode());
        assertEquals(2, calls.get());
    }
}
