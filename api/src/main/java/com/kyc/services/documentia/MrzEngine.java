package com.kyc.services.documentia;

import com.kyc.dto.documentia.MrzReport;
import com.kyc.dto.documentia.MrzReport.CheckDigit;
import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ValidationIssue;
import com.kyc.enums.FieldValueType;
import com.kyc.enums.MrzFormat;
import com.kyc.services.documentia.SchemaEdition.FieldDef;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Chiffres ICAO 9303 recalculés. Le chiffre hors de la ligne MRZ n'est pas relu. */
public final class MrzEngine {

    private static final int[] WEIGHTS = {7, 3, 1};
    private static final Map<Character, char[]> OCR = Map.of(
            '0', new char[] {'O'},
            'O', new char[] {'0'},
            '1', new char[] {'I'},
            'I', new char[] {'1'},
            '2', new char[] {'Z'},
            'Z', new char[] {'2'},
            '5', new char[] {'S'},
            'S', new char[] {'5'},
            '8', new char[] {'B'},
            'B', new char[] {'8'});

    private MrzEngine() {}

    public record Outcome(List<NormalizedField> fields, List<ValidationIssue> validation, MrzReport report) {}

    public static int checkDigit(String data) {
        int sum = 0;
        for (int i = 0; i < data.length(); i++) {
            sum += value(data.charAt(i)) * WEIGHTS[i % 3];
        }
        return sum % 10;
    }

    public static Outcome read(
            SchemaEdition edition,
            ParsedDocument document,
            FieldNormalizer.Result normalization,
            LocalDate today) {
        if (edition.mrzFormat() == null || edition.mrzFormat() == MrzFormat.NONE) {
            return new Outcome(
                    normalization.fields(),
                    normalization.validation(),
                    new MrzReport("NONE", "NOT_APPLICABLE", List.of(), List.of(), null));
        }
        List<String> lines = find(document, edition, normalization.fields());
        if (lines == null) {
            List<ValidationIssue> validation = new ArrayList<>(normalization.validation());
            validation.add(new ValidationIssue("L4", "mrz_unavailable", "WARNING", "mrz"));
            return new Outcome(
                    normalization.fields(),
                    List.copyOf(validation),
                    new MrzReport(edition.mrzFormat().name(), "UNAVAILABLE", List.of(), List.of(), 0.0));
        }
        Reading reading = accept(edition.mrzFormat(), lines);
        List<NormalizedField> fields = new ArrayList<>();
        List<ValidationIssue> validation = new ArrayList<>(normalization.validation());
        boolean mismatch = false;
        if (reading.valid()) {
            for (NormalizedField field : normalization.fields()) {
                String mrzKey = mrzKey(field.field(), reading, today);
                String visual = "VALID".equals(field.validationStatus()) ? field.comparisonKey() : null;
                if (mrzKey == null || visual == null) {
                    fields.add(field);
                    continue;
                }
                if (visual.equals(mrzKey)) {
                    fields.add(new NormalizedField(
                            field.field(),
                            field.value(),
                            field.normalizedValue(),
                            field.comparisonKey(),
                            field.confidence(),
                            "VISUAL_AND_MRZ",
                            field.validationStatus()));
                } else {
                    mismatch = true;
                    fields.add(new NormalizedField(
                            field.field(),
                            field.value(),
                            field.normalizedValue(),
                            field.comparisonKey(),
                            field.confidence(),
                            field.source(),
                            "MISMATCH"));
                    validation.add(new ValidationIssue("L4", "mrz_mismatch", "WARNING", field.field()));
                }
            }
        } else {
            fields.addAll(normalization.fields());
            validation.add(new ValidationIssue("L4", "mrz_check_digit_failed", "WARNING", "mrz"));
        }
        double score = reading.valid() && !mismatch ? 1.0 : 0.4;
        String status = !reading.valid() ? "FAILED" : mismatch ? "MISMATCH" : "VALID";
        return new Outcome(
                List.copyOf(fields),
                List.copyOf(validation),
                new MrzReport(edition.mrzFormat().name(), status, reading.lines(), reading.checks(), score));
    }

    static Reading accept(MrzFormat format, List<String> lines) {
        Reading direct = parse(format, lines);
        if (direct.valid()) {
            return direct;
        }
        Reading fixed = onlyCorrection(format, lines);
        return fixed == null ? direct : fixed;
    }

    private static Reading onlyCorrection(MrzFormat format, List<String> lines) {
        Reading found = null;
        for (int line = 0; line < lines.size(); line++) {
            String value = lines.get(line);
            for (int i = 0; i < value.length(); i++) {
                char[] alternatives = OCR.get(value.charAt(i));
                if (alternatives == null) {
                    continue;
                }
                for (char alternative : alternatives) {
                    List<String> copy = new ArrayList<>(lines);
                    char[] chars = value.toCharArray();
                    chars[i] = alternative;
                    copy.set(line, new String(chars));
                    Reading reading = parse(format, copy);
                    if (!reading.valid()) {
                        continue;
                    }
                    if (found != null) {
                        return null;
                    }
                    found = reading;
                }
            }
        }
        return found;
    }

    private static Reading parse(MrzFormat format, List<String> lines) {
        return switch (format) {
            case TD3 -> td3(lines);
            case TD2 -> td2(lines);
            case TD1 -> td1(lines);
            case NONE -> new Reading(lines, emptyIdentity(), List.of(), false);
        };
    }

    private static Reading td3(List<String> lines) {
        String line1 = lines.get(0);
        String line2 = lines.get(1);
        Names names = names(line1.substring(5));
        List<CheckDigit> checks = List.of(
                digit("documentNumber", line2.charAt(9), line2.substring(0, 9)),
                digit("dateOfBirth", line2.charAt(19), line2.substring(13, 19)),
                digit("expirationDate", line2.charAt(27), line2.substring(21, 27)),
                digit("optional", line2.charAt(42), line2.substring(28, 42)),
                digit("composite", line2.charAt(43), line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 43)));
        return identity(lines, names, clean(line2.substring(0, 9)), line2.substring(13, 19), line2.substring(21, 27), line2.charAt(20), line2.substring(10, 13), checks);
    }

    private static Reading td2(List<String> lines) {
        String line1 = lines.get(0);
        String line2 = lines.get(1);
        Names names = names(line1.substring(5));
        List<CheckDigit> checks = List.of(
                digit("documentNumber", line2.charAt(9), line2.substring(0, 9)),
                digit("dateOfBirth", line2.charAt(19), line2.substring(13, 19)),
                digit("expirationDate", line2.charAt(27), line2.substring(21, 27)),
                digit("composite", line2.charAt(35), line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 35)));
        return identity(lines, names, clean(line2.substring(0, 9)), line2.substring(13, 19), line2.substring(21, 27), line2.charAt(20), line2.substring(10, 13), checks);
    }

    private static Reading td1(List<String> lines) {
        String line1 = lines.get(0);
        String line2 = lines.get(1);
        String line3 = lines.get(2);
        Names names = names(line3);
        String composite = line1.substring(5, 30)
                + line2.substring(0, 7)
                + line2.substring(8, 15)
                + line2.substring(18, 29);
        List<CheckDigit> checks = List.of(
                digit("documentNumber", line1.charAt(14), line1.substring(5, 14)),
                digit("dateOfBirth", line2.charAt(6), line2.substring(0, 6)),
                digit("expirationDate", line2.charAt(14), line2.substring(8, 14)),
                digit("composite", line2.charAt(29), composite));
        return identity(lines, names, clean(line1.substring(5, 14)), line2.substring(0, 6), line2.substring(8, 14), line2.charAt(7), line2.substring(15, 18), checks);
    }

    private static Reading identity(
            List<String> lines,
            Names names,
            String number,
            String birth,
            String expiry,
            char sex,
            String nationality,
            List<CheckDigit> checks) {
        boolean valid = checks.stream().allMatch(CheckDigit::valid);
        return new Reading(
                lines,
                new Identity(names.lastName(), names.firstName(), number, birth, expiry, sexOf(sex), clean(nationality)),
                checks,
                valid);
    }

    private static CheckDigit digit(String field, char printed, String data) {
        String calculated = Integer.toString(checkDigit(data));
        return new CheckDigit(field, String.valueOf(printed), calculated, calculated.equals(String.valueOf(printed)));
    }

    private static Names names(String raw) {
        String trimmed = raw.replaceAll("<+$", "");
        String[] parts = trimmed.split("<<", 2);
        return new Names(words(parts[0]), parts.length == 1 ? "" : words(parts[1]));
    }

    private static String words(String raw) {
        return raw.replace('<', ' ').trim().replaceAll(" +", " ");
    }

    private static String clean(String raw) {
        String value = raw.replace("<", "").trim();
        return value.isEmpty() ? null : value;
    }

    private static String sexOf(char sex) {
        return sex == 'M' || sex == 'F' ? String.valueOf(sex) : null;
    }

    private static String mrzKey(String field, Reading reading, LocalDate today) {
        Identity identity = reading.identity();
        return switch (field) {
            case "lastName" -> identity.lastName();
            case "firstName" -> blankToNull(identity.firstName());
            case "documentNumber" -> identity.documentNumber();
            case "dateOfBirth" -> iso(identity.birth(), today, true);
            case "expirationDate" -> iso(identity.expiry(), today, false);
            case "sex" -> identity.sex();
            case "nationality" -> identity.nationality();
            default -> null;
        };
    }

    private static String iso(String yymmdd, LocalDate today, boolean birth) {
        if (yymmdd == null || yymmdd.length() != 6 || !yymmdd.chars().allMatch(Character::isDigit)) {
            return null;
        }
        int year = Integer.parseInt(yymmdd.substring(0, 2));
        int century = 2000;
        if (birth && birthFallsInThePreviousCentury(yymmdd, today, year)) {
            century = 1900;
        }
        return (century + year) + "-" + yymmdd.substring(2, 4) + "-" + yymmdd.substring(4, 6);
    }

    private static boolean birthFallsInThePreviousCentury(String yymmdd, LocalDate today, int year) {
        try {
            int month = Integer.parseInt(yymmdd.substring(2, 4));
            int day = Integer.parseInt(yymmdd.substring(4, 6));
            return LocalDate.of(2000 + year, month, day).isAfter(today);
        } catch (DateTimeException ex) {
            return year > today.getYear() % 100;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static List<String> find(ParsedDocument document, SchemaEdition edition, List<NormalizedField> fields) {
        int width = width(edition.mrzFormat());
        int count = edition.mrzFormat() == MrzFormat.TD1 ? 3 : 2;
        List<String> sources = new ArrayList<>();
        for (FieldDef definition : edition.fields()) {
            if (definition.type() != FieldValueType.MRZ) {
                continue;
            }
            for (NormalizedField field : fields) {
                if (definition.name().equals(field.field()) && field.value() != null) {
                    sources.add(field.value());
                }
            }
        }
        if (document.rawText() != null) {
            sources.add(document.rawText());
        }
        for (String source : sources) {
            List<String> lines = block(source, width, count);
            if (lines != null) {
                return lines;
            }
        }
        return null;
    }

    private static List<String> block(String text, int width, int count) {
        List<String> exact = new ArrayList<>();
        for (String line : text.toUpperCase(Locale.ROOT).split("\\R")) {
            String compact = line.replace(" ", "");
            if (compact.length() == width && alphabet(compact)) {
                exact.add(compact);
            }
        }
        if (exact.size() >= count) {
            return List.copyOf(exact.subList(0, count));
        }
        String compact = text.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (compact.length() == width * count && alphabet(compact)) {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                lines.add(compact.substring(i * width, (i + 1) * width));
            }
            return lines;
        }
        return null;
    }

    private static boolean alphabet(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '<' && (c < '0' || c > '9') && (c < 'A' || c > 'Z')) {
                return false;
            }
        }
        return true;
    }

    private static int value(char c) {
        if (c == '<') {
            return 0;
        }
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        return 0;
    }

    private static int width(MrzFormat format) {
        return switch (format) {
            case TD1 -> 30;
            case TD2 -> 36;
            case TD3 -> 44;
            case NONE -> 0;
        };
    }

    private static Identity emptyIdentity() {
        return new Identity(null, null, null, null, null, null, null);
    }

    record Reading(List<String> lines, Identity identity, List<CheckDigit> checks, boolean valid) {}

    record Identity(
            String lastName,
            String firstName,
            String documentNumber,
            String birth,
            String expiry,
            String sex,
            String nationality) {}

    private record Names(String lastName, String firstName) {}
}
