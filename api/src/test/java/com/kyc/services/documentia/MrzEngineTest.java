package com.kyc.services.documentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ParsedDocument.Classification;
import com.kyc.dto.documentia.ParsedDocument.ExtractedField;
import com.kyc.enums.FieldValueType;
import com.kyc.enums.MrzFormat;
import com.kyc.services.documentia.SchemaEdition.FieldDef;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MrzEngineTest {

    static final String TD3_LINE_1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    static final String TD3_LINE_2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    private static final LocalDate TODAY = LocalDate.parse("2026-10-07");

    @Test
    void icaoTd3SpecimenRecalculatesEveryCheckDigit() {
        assertEquals(44, TD3_LINE_1.length());
        assertEquals(44, TD3_LINE_2.length());
        MrzEngine.Outcome outcome = MrzEngine.read(passport(), specimenDocument(), normalize(specimenDocument()), TODAY);

        assertEquals("VALID", outcome.report().status());
        assertEquals(1.0, outcome.report().mrzScore());
        assertEquals(5, outcome.report().checkDigits().size());
        assertTrue(outcome.report().checkDigits().stream().allMatch(digit -> digit.valid()));
        assertEquals("6", digit(outcome, "documentNumber").printed());
        assertEquals("6", digit(outcome, "documentNumber").calculated());
        assertEquals("VISUAL_AND_MRZ", field(outcome, "lastName").source());
        assertEquals("VISUAL_AND_MRZ", field(outcome, "documentNumber").source());
        assertEquals("VISUAL_AND_MRZ", field(outcome, "dateOfBirth").source());
    }

    @Test
    void oneOcrSubstitutionRestoresTheCheckDigits() {
        String broken = "L898902C36UTO7408122F12041S9ZE184226B<<<<<10";
        MrzEngine.Reading reading = MrzEngine.accept(MrzFormat.TD3, List.of(TD3_LINE_1, broken));

        assertTrue(reading.valid());
        assertEquals(TD3_LINE_2, reading.lines().get(1));
    }

    @Test
    void twoCorruptionsStayFailed() {
        String broken = "LBB8902C36UTO7408122F1204159ZE184226B<<<<<10";
        MrzEngine.Reading reading = MrzEngine.accept(MrzFormat.TD3, List.of(TD3_LINE_1, broken));

        assertFalse(reading.valid());
    }

    @Test
    void differentNameIsAMismatch() {
        ParsedDocument document = document(List.of(
                new ExtractedField("lastName", "TREMBLAY", null, "VISUAL_TEXT"),
                new ExtractedField("mrz", TD3_LINE_1 + "\n" + TD3_LINE_2, null, "MRZ")));
        MrzEngine.Outcome outcome = MrzEngine.read(passport(), document, normalize(document), TODAY);

        assertEquals("MISMATCH", outcome.report().status());
        assertEquals(0.4, outcome.report().mrzScore());
        assertEquals("MISMATCH", field(outcome, "lastName").validationStatus());
        assertTrue(outcome.validation().stream().anyMatch(issue -> "mrz_mismatch".equals(issue.code())
                && "L4".equals(issue.level())
                && "WARNING".equals(issue.severity())));
    }

    @Test
    void quebecFormatDoesNotReadAnMrz() {
        SchemaEdition quebec = new SchemaEdition(
                UUID.randomUUID(),
                "QUEBEC_DRIVER_LICENSE",
                "CA",
                "DRIVING_LICENSE",
                "2024",
                "FRONT",
                "QC",
                List.of(new FieldDef("lastName", FieldValueType.NAME, true, "ICAO_NAME", null)),
                List.of(),
                MrzFormat.NONE);
        ParsedDocument document = document(List.of(new ExtractedField("lastName", "TREMBLAY", null, "VISUAL_TEXT")));
        MrzEngine.Outcome outcome = MrzEngine.read(quebec, document, normalize(document), TODAY);

        assertEquals("NOT_APPLICABLE", outcome.report().status());
        assertNull(outcome.report().mrzScore());
        assertTrue(outcome.report().checkDigits().isEmpty());
        assertFalse(outcome.validation().stream().anyMatch(issue -> "mrz_unavailable".equals(issue.code())));
    }

    @Test
    void canadaPermanentResidentExpiryUsesTheNextCentury() {
        String mrz = """
                CACANPD40779087<1113098892<<<5
                9204096M3010219CMR<251021<01<3
                ONANA<ONANA<<JOSEPH<JUNIOR<<<<""";
        SchemaEdition card = new SchemaEdition(
                UUID.randomUUID(),
                "CANADA_PERMANENT_RESIDENT",
                "CA",
                "RESIDENCE_PERMIT",
                "2015",
                "BACK",
                null,
                List.of(
                        new FieldDef("documentNumber", FieldValueType.DOCUMENT_NUMBER, true, null, null),
                        new FieldDef("lastName", FieldValueType.NAME, false, "ICAO_NAME", null),
                        new FieldDef("dateOfBirth", FieldValueType.DATE, false, "ISO_DATE", "yyyy-MM-dd"),
                        new FieldDef("expirationDate", FieldValueType.DATE, false, "ISO_DATE", "yyyy-MM-dd"),
                        new FieldDef("mrz", FieldValueType.MRZ, true, null, null)),
                List.of(),
                MrzFormat.TD1);
        ParsedDocument document = text(mrz, List.of(
                new ExtractedField("documentNumber", "PD4077908", null, "VISUAL_TEXT"),
                new ExtractedField("lastName", "ONANA ONANA", null, "VISUAL_TEXT"),
                new ExtractedField("dateOfBirth", "1992-04-09", null, "VISUAL_TEXT"),
                new ExtractedField("expirationDate", "2030-10-21", null, "VISUAL_TEXT"),
                new ExtractedField("mrz", mrz, null, "MRZ")));
        MrzEngine.Outcome outcome = MrzEngine.read(card, document, FieldNormalizer.normalize(card, document, TODAY), TODAY);

        assertEquals("VALID", outcome.report().status());
        assertEquals("2030-10-21", field(outcome, "expirationDate").normalizedValue());
        assertEquals("1992-04-09", field(outcome, "dateOfBirth").normalizedValue());
        assertEquals("VISUAL_AND_MRZ", field(outcome, "expirationDate").source());
        assertEquals("VISUAL_AND_MRZ", field(outcome, "documentNumber").source());
    }

    @Test
    void td1AndTd2RoundTripTheirOwnCheckDigits() {
        assertTrue(roundTrip(MrzFormat.TD1, td1()).valid());
        assertTrue(roundTrip(MrzFormat.TD2, td2()).valid());
        assertEquals("D23145890", roundTrip(MrzFormat.TD1, td1()).identity().documentNumber());
        assertEquals("ERIKSSON", roundTrip(MrzFormat.TD1, td1()).identity().lastName());
        assertEquals("ERIKSSON", roundTrip(MrzFormat.TD2, td2()).identity().lastName());
    }

    @Test
    void missingBlockIsUnavailable() {
        ParsedDocument document = text(null, List.of(new ExtractedField("lastName", "ERIKSSON", null, "VISUAL_TEXT")));
        MrzEngine.Outcome outcome = MrzEngine.read(passport(), document, normalize(document), TODAY);

        assertEquals("UNAVAILABLE", outcome.report().status());
        assertEquals(0.0, outcome.report().mrzScore());
        assertTrue(outcome.validation().stream().anyMatch(issue -> "mrz_unavailable".equals(issue.code())));
    }

    private static MrzEngine.Reading roundTrip(MrzFormat format, List<String> lines) {
        return MrzEngine.accept(format, lines);
    }

    private static List<String> td1() {
        String number = "D23145890";
        String birth = "740812";
        String expiry = "120415";
        String optional = "<<<<<<<<<<<<<<<";
        String line1 = "I<UTO" + number + MrzEngine.checkDigit(number) + optional;
        String tail = "UTO<<<<<<<<<<<";
        String line2Body = birth
                + MrzEngine.checkDigit(birth)
                + "F"
                + expiry
                + MrzEngine.checkDigit(expiry)
                + tail;
        String composite = line1.substring(5, 30)
                + line2Body.substring(0, 7)
                + line2Body.substring(8, 15)
                + line2Body.substring(18, 29);
        String line2 = line2Body.substring(0, 29) + MrzEngine.checkDigit(composite);
        String line3 = "ERIKSSON<<ANNA<MARIA<<<<<<<<<<";
        assertEquals(30, line1.length());
        assertEquals(30, line2.length());
        assertEquals(30, line3.length());
        return List.of(line1, line2, line3);
    }

    private static List<String> td2() {
        String number = "D23145890";
        String birth = "740812";
        String expiry = "120415";
        String line2Body = number
                + MrzEngine.checkDigit(number)
                + "UTO"
                + birth
                + MrzEngine.checkDigit(birth)
                + "F"
                + expiry
                + MrzEngine.checkDigit(expiry)
                + "<<<<<<<";
        String composite = line2Body.substring(0, 10) + line2Body.substring(13, 20) + line2Body.substring(21, 35);
        String line1 = "I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<";
        String line2 = line2Body + MrzEngine.checkDigit(composite);
        assertEquals(36, line1.length());
        assertEquals(36, line2.length());
        return List.of(line1, line2);
    }

    private static SchemaEdition passport() {
        return new SchemaEdition(
                SchemaRegistry.PASSPORT_VERSION_ID,
                SchemaRegistry.PASSPORT_CODE,
                "UT",
                "PASSPORT",
                "2024",
                "FRONT",
                null,
                List.of(
                        new FieldDef("lastName", FieldValueType.NAME, true, "ICAO_NAME", null),
                        new FieldDef("firstName", FieldValueType.NAME, true, "ICAO_NAME", null),
                        new FieldDef("documentNumber", FieldValueType.DOCUMENT_NUMBER, true, null, null),
                        new FieldDef("dateOfBirth", FieldValueType.DATE, true, "ISO_DATE", "yyyy-MM-dd"),
                        new FieldDef("expirationDate", FieldValueType.DATE, true, "ISO_DATE", "yyyy-MM-dd"),
                        new FieldDef("sex", FieldValueType.SEX, true, null, null),
                        new FieldDef("mrz", FieldValueType.MRZ, true, null, null)),
                List.of(),
                MrzFormat.TD3);
    }

    private static ParsedDocument specimenDocument() {
        return document(List.of(
                new ExtractedField("lastName", "ERIKSSON", null, "VISUAL_TEXT"),
                new ExtractedField("firstName", "ANNA MARIA", null, "VISUAL_TEXT"),
                new ExtractedField("documentNumber", "L898902C3", null, "VISUAL_TEXT"),
                new ExtractedField("dateOfBirth", "1974-08-12", null, "VISUAL_TEXT"),
                new ExtractedField("expirationDate", "2012-04-15", null, "VISUAL_TEXT"),
                new ExtractedField("sex", "F", null, "VISUAL_TEXT"),
                new ExtractedField("mrz", TD3_LINE_1 + "\n" + TD3_LINE_2, null, "MRZ")));
    }

    private static ParsedDocument document(List<ExtractedField> fields) {
        return text(TD3_LINE_1 + "\n" + TD3_LINE_2, fields);
    }

    private static ParsedDocument text(String rawText, List<ExtractedField> fields) {
        return new ParsedDocument(
                true,
                "DOCUMENT_PRESENT",
                null,
                new Classification("PASSPORT_TD3", "UT", null, "PASSPORT", "2024", "FRONT", 0.9),
                rawText,
                List.of(),
                fields,
                List.of(),
                null);
    }

    private static FieldNormalizer.Result normalize(ParsedDocument document) {
        SchemaEdition edition = document.fields().stream().anyMatch(field -> "mrz".equals(field.field()))
                ? passport()
                : passport();
        return FieldNormalizer.normalize(edition, document, TODAY);
    }

    private static NormalizedField field(MrzEngine.Outcome outcome, String name) {
        return outcome.fields().stream().filter(field -> name.equals(field.field())).findFirst().orElseThrow();
    }

    private static com.kyc.dto.documentia.MrzReport.CheckDigit digit(MrzEngine.Outcome outcome, String name) {
        return outcome.report().checkDigits().stream()
                .filter(digit -> name.equals(digit.field()))
                .findFirst()
                .orElseThrow();
    }
}
