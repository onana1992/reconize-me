package com.kyc.services.documentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                        "The left date after Valide le is dateOfIssue. The right date after Expire le is expirationDate.",
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
                        null,
                        List.of())));
        assertTrue(prompt.contains("QUEBEC_DRIVER_LICENSE"));
        assertTrue(prompt.contains("CAMEROON_NATIONAL_ID"));
        assertTrue(prompt.contains("2016"));
        assertTrue(prompt.contains("expirationDate"));
        assertTrue(prompt.contains("Expire le"));
        assertTrue(prompt.contains("reading: The left date after Valide le is dateOfIssue."));
        assertTrue(VisionPrompt.fromCatalog(List.of(), "BACK").contains("Capture side: BACK"));
    }

    @Test
    void frontCaptureKeepsTheOtherSideShort() {
        String prompt = VisionPrompt.fromCatalog(
                List.of(
                        new Schema(
                                VERSION,
                                "QUEBEC_DRIVER_LICENSE",
                                "CA",
                                "DRIVING_LICENSE",
                                "FRONT",
                                "2024",
                                "QC",
                                "Quebec license front.",
                                List.of(new com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt(
                                        "expirationDate", true, "Expire le"))),
                        new Schema(
                                UUID.randomUUID(),
                                "QUEBEC_DRIVER_LICENSE",
                                "CA",
                                "DRIVING_LICENSE",
                                "BACK",
                                "2024",
                                "QC",
                                "Quebec license back. The long barcode instructions are not needed to notice this side.",
                                List.of(new com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt(
                                        "barcode", true, "PDF417 at the upper left")))),
                "FRONT");

        assertTrue(prompt.contains("expirationDate"));
        assertTrue(prompt.contains("Capture side: FRONT"));
        assertFalse(prompt.contains("side: BACK"));
        assertFalse(prompt.contains("PDF417"));
        assertEquals(
                "low",
                VisionPrompt.imageDetail(
                        "BACK",
                        List.of(new Schema(
                                VERSION,
                                "QUEBEC_DRIVER_LICENSE",
                                "CA",
                                "DRIVING_LICENSE",
                                "BACK",
                                "2024",
                                "QC",
                                "Quebec license back.",
                                List.of(new com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt(
                                        "barcode", true, "PDF417"))))));
        assertEquals(
                "high",
                VisionPrompt.imageDetail(
                        "BACK",
                        List.of(new Schema(
                                VERSION,
                                "CANADA_PERMANENT_RESIDENT",
                                "CA",
                                "RESIDENCE_PERMIT",
                                "BACK",
                                "2015",
                                null,
                                "Canada permanent resident card back.",
                                List.of(new com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt(
                                        "mrz", true, "three lines"))))));
    }

    @Test
    void backMrzReadingReplacesTheLongCatalogEssay() {
        String prompt = VisionPrompt.fromCatalog(
                List.of(new Schema(
                        VERSION,
                        "CANADA_PERMANENT_RESIDENT",
                        "CA",
                        "RESIDENCE_PERMIT",
                        "BACK",
                        "2015",
                        null,
                        "Canada permanent resident card back. Second line positions, counted from 1: 1-6 birth date. Do not take the day from the later optional date. The line ends with fillers.",
                        List.of(
                                new com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt(
                                        "documentNumber", true, "the PD number printed above the machine-readable zone"),
                                new com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt(
                                        "expirationDate", false, "second MRZ line positions 9-14"),
                                new com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt(
                                        "mrz", true, "three lines of 30 characters")))),
                "BACK");

        assertTrue(prompt.contains("three lines of 30 characters"));
        assertTrue(prompt.contains("the PD number printed above the machine-readable zone"));
        assertFalse(prompt.contains("positions 9-14"));
        assertFalse(prompt.contains("later optional date"));
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
    void rejectedJsonIsNotRetried() {
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
        assertEquals(1, calls.get());
    }
}
