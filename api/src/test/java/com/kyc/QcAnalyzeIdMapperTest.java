package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.QcAnalyzeIdMapper;
import com.kyc.ports.DocumentAiPort.DocumentSignals;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class QcAnalyzeIdMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsQcFixtureToDrivingLicense() throws Exception {
        String json = StreamUtils.copyToString(
                new ClassPathResource("fixtures/analyzeid-qc.json").getInputStream(), StandardCharsets.UTF_8);
        DocumentSignals signals = new QcAnalyzeIdMapper(
                        Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC))
                .map(mapper.readTree(json));
        assertTrue(signals.supported());
        assertEquals("driving_license", signals.documentType());
        assertEquals("CA", signals.documentCountry());
        assertEquals("QC", signals.issuingJurisdiction());
        assertEquals("ANNE-MARIE", signals.firstName());
        assertEquals("TREMBLAY", signals.lastName());
        assertEquals(LocalDate.of(1990, 4, 12), signals.birthDate());
        assertEquals(LocalDate.of(2028, 6, 1), signals.expirationDate());
        assertEquals("T1234-567890-12", signals.documentNumber());
        assertFalse(signals.expired());
        assertEquals("textract_analyze_id", signals.provider());
    }

    @Test
    void ontarioIsUnsupported() throws Exception {
        String json =
                """
                {"IdentityDocuments":[{"IdentityDocumentFields":[
                  {"Type":{"Text":"ID_TYPE"},"ValueDetection":{"Text":"DRIVER LICENSE"}},
                  {"Type":{"Text":"STATE_NAME"},"ValueDetection":{"Text":"ONTARIO"}},
                  {"Type":{"Text":"COUNTRY"},"ValueDetection":{"Text":"CANADA"}},
                  {"Type":{"Text":"FIRST_NAME"},"ValueDetection":{"Text":"A"}},
                  {"Type":{"Text":"LAST_NAME"},"ValueDetection":{"Text":"B"}},
                  {"Type":{"Text":"DATE_OF_BIRTH"},"ValueDetection":{"Text":"1990-01-01"}},
                  {"Type":{"Text":"EXPIRATION_DATE"},"ValueDetection":{"Text":"2030-01-01"}},
                  {"Type":{"Text":"DOCUMENT_NUMBER"},"ValueDetection":{"Text":"X"}}
                ]}]}
                """;
        DocumentSignals signals = new QcAnalyzeIdMapper().map(mapper.readTree(json));
        assertFalse(signals.supported());
    }
}
