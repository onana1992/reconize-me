package com.kyc.services.documentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.dto.documentia.DocumentParse;
import com.kyc.dto.documentia.ParsedDocument;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentAnalysisParserTest {

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
                Set.of("firstName", "lastName", "dateOfBirth", "documentNumber", "expirationDate")));
    });

    @Test
    void keepsKnownCodeWhenClassConfidenceIsLow() {
        DocumentParse parsed = parser.parse("""
                {
                  "detection": "DOCUMENT_PRESENT",
                  "classification": { "code": "QUEBEC_DRIVER_LICENSE", "version": "2024", "confidence": 0.5 }
                }
                """);
        assertEquals("QUEBEC_DRIVER_LICENSE", parsed.document().classification().code());
        assertEquals(0.5, parsed.document().classification().confidence());
        assertEquals("CA", parsed.document().classification().country());
        assertEquals("QC", parsed.document().classification().issuingJurisdiction());
        assertEquals("FRONT", parsed.document().classification().side());
        assertEquals(List.of("DOCUMENT_UNKNOWN"), parsed.document().indicators());
        assertEquals(VERSION, parsed.schemaVersionId());
    }

    @Test
    void unknownVersionIsNotBoundToAnEdition() {
        DocumentParse parsed = parser.parse("""
                {
                  "detection": "DOCUMENT_PRESENT",
                  "classification": { "code": "QUEBEC_DRIVER_LICENSE", "version": "1999", "confidence": 0.94 }
                }
                """);
        assertEquals("1999", parsed.document().classification().version());
        assertEquals(List.of("unexpected_document_structure"), parsed.document().indicators());
        assertNull(parsed.schemaVersionId());
    }

    @Test
    void matchingEditionKeepsItsOwnFields() {
        UUID edition2016 = UUID.fromString("018f5a00-0000-7000-8000-0000000000b1");
        UUID edition2024 = UUID.fromString("018f5a00-0000-7000-8000-0000000000b2");
        DocumentAnalysisParser multi = new DocumentAnalysisParser(new ObjectMapper(), code -> {
            if (!"CAMEROON_NATIONAL_ID".equals(code)) {
                return List.of();
            }
            return List.of(
                    new ActiveSchema(
                            edition2016,
                            "CAMEROON_NATIONAL_ID",
                            "CM",
                            "NATIONAL_ID",
                            "2016",
                            List.of("FRONT"),
                            null,
                            Set.of("lastName", "nin")),
                    new ActiveSchema(
                            edition2024,
                            "CAMEROON_NATIONAL_ID",
                            "CM",
                            "NATIONAL_ID",
                            "2024",
                            List.of("FRONT"),
                            null,
                            Set.of("lastName", "documentNumber")));
        });
        DocumentParse parsed = multi.parse("""
                {
                  "detection": "DOCUMENT_PRESENT",
                  "classification": { "code": "CAMEROON_NATIONAL_ID", "version": "2016", "confidence": 0.91 },
                  "fields": [
                    { "field": "nin", "value": "123" },
                    { "field": "documentNumber", "value": "DROP" }
                  ]
                }
                """);
        assertEquals("2016", parsed.document().classification().version());
        assertEquals("CAMEROON_NATIONAL_ID", parsed.document().classification().code());
        assertEquals(edition2016, parsed.schemaVersionId());
        assertTrue(parsed.document().indicators().isEmpty());
        assertEquals(1, parsed.document().fields().size());
        assertEquals("nin", parsed.document().fields().get(0).field());
    }

    @Test
    void dropsFieldsAbsentFromSchemaAndModelNormalizedValue() {
        ParsedDocument parsed = parser.parse("""
                {
                  "detection": "DOCUMENT_PRESENT",
                  "classification": { "code": "QUEBEC_DRIVER_LICENSE", "version": "2024", "confidence": 0.94 },
                  "zones": [
                    { "id": "z2", "text": "HORS", "boundingBox": { "x": 1.2, "y": 0.1, "width": 0.2, "height": 0.05 } }
                  ],
                  "fields": [
                    { "field": "lastName", "value": "TREMBLAY", "normalizedValue": "IGNORED", "checkDigit": "7", "source": "VISUAL_TEXT" },
                    { "field": "notOnSchema", "value": "DROP" }
                  ]
                }
                """).document();
        assertEquals("HORS", parsed.zones().get(0).text());
        assertNull(parsed.zones().get(0).boundingBox());
        assertEquals(1, parsed.fields().size());
        assertEquals("TREMBLAY", parsed.fields().get(0).value());
        assertEquals("VISUAL_TEXT", parsed.fields().get(0).source());
    }

    @Test
    void unknownCodeDropsBusinessFields() {
        DocumentParse parsed = parser.parse("""
                {
                  "detection": "DOCUMENT_PRESENT",
                  "classification": { "code": "MARS_PASSPORT", "confidence": 0.99 },
                  "fields": [ { "field": "lastName", "value": "ONANA" } ]
                }
                """);
        assertEquals("UNKNOWN", parsed.document().classification().code());
        assertTrue(parsed.document().fields().isEmpty());
        assertNull(parsed.schemaVersionId());
    }

    @Test
    void readableIgnoresModelFlagsThatAreNotSevere() {
        ParsedDocument parsed = parser.parse("""
                {
                  "detection": "DOCUMENT_PRESENT",
                  "documentDetected": false,
                  "quality": { "readable": false, "tilted": true, "unreadable": true },
                  "qualityScore": 0.1,
                  "classification": { "code": "QUEBEC_DRIVER_LICENSE", "version": "2024" }
                }
                """).document();
        assertTrue(parsed.documentDetected());
        assertTrue(parsed.quality().readable());
        assertTrue(parsed.quality().tilted());
        assertFalse(parsed.quality().unreadable());
    }

    @Test
    void keepsOnlyClosedModelIndicators() {
        ParsedDocument parsed = parser.parse("""
                {
                  "detection": "DOCUMENT_PRESENT",
                  "classification": { "code": "QUEBEC_DRIVER_LICENSE", "version": "2024", "confidence": 0.94 },
                  "indicators": ["possible_alteration", "FORGED", "document_expired"]
                }
                """).document();
        assertEquals(List.of("possible_alteration"), parsed.indicators());
    }

    @Test
    void explicitBackBindsTheBackEdition() {
        UUID front = UUID.fromString("018f5a00-0000-7000-8000-0000000000a2");
        UUID back = UUID.fromString("018f5a00-0000-7000-8000-0000000000c2");
        DocumentAnalysisParser sided = new DocumentAnalysisParser(new ObjectMapper(), code -> {
            if (!"QUEBEC_DRIVER_LICENSE".equals(code)) {
                return List.of();
            }
            return List.of(
                    new ActiveSchema(front, "QUEBEC_DRIVER_LICENSE", "CA", "DRIVING_LICENSE", "2024", List.of("FRONT"), "QC",
                            Set.of("firstName", "lastName", "expirationDate")),
                    new ActiveSchema(back, "QUEBEC_DRIVER_LICENSE", "CA", "DRIVING_LICENSE", "2024", List.of("BACK"), "QC",
                            Set.of("barcode")));
        });
        DocumentParse parsed = sided.parse("""
                {
                  "detection": "DOCUMENT_PRESENT",
                  "classification": { "code": "QUEBEC_DRIVER_LICENSE", "version": "2024", "side": "BACK", "confidence": 0.97 },
                  "fields": [
                    { "field": "barcode", "value": "PRESENT", "confidence": 0.96, "source": "VISUAL_TEXT" },
                    { "field": "lastName", "value": "ONANA", "confidence": 0.9, "source": "VISUAL_TEXT" }
                  ]
                }
                """);
        assertEquals("BACK", parsed.document().classification().side());
        assertEquals(back, parsed.schemaVersionId());
        assertEquals(1, parsed.document().fields().size());
        assertEquals("barcode", parsed.document().fields().get(0).field());
    }

    @Test
    void rejectsUnknownDetection() {
        assertThrows(InvalidModelJsonException.class, () -> parser.parse("{ \"detection\": \"SELFIE\" }"));
    }

    @Test
    void rejectsBrokenJson() {
        assertThrows(InvalidModelJsonException.class, () -> parser.parse("{"));
    }
}
