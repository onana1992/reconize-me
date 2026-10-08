package com.kyc.services.documentia;

import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ParsedDocument.ExtractedField;
import com.kyc.dto.documentia.ValidationIssue;
import com.kyc.enums.FieldValueType;
import com.kyc.services.documentia.SchemaEdition.FieldDef;
import com.kyc.services.documentia.SchemaEdition.RuleDef;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Valeur brute immuable. normalizedValue et les statuts L1–L3 sont calculés ici. */
public final class FieldNormalizer {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern COMPARISON =
            Pattern.compile("^([A-Za-z][A-Za-z0-9]*)\\s*(<=|>=|<|>)\\s*([A-Za-z][A-Za-z0-9]*)$");
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
    private static final Map<String, String> SEX = Map.ofEntries(
            Map.entry("M", "M"),
            Map.entry("F", "F"),
            Map.entry("X", "X"),
            Map.entry("MALE", "M"),
            Map.entry("FEMALE", "F"),
            Map.entry("MASCULIN", "M"),
            Map.entry("FEMININ", "F"),
            Map.entry("MASCULINE", "M"),
            Map.entry("FEMININE", "F"));

    private FieldNormalizer() {}

    public record Result(
            List<NormalizedField> fields, List<ValidationIssue> validation, Map<String, Object> extractedIdentity) {}

    public static Result normalize(SchemaEdition edition, ParsedDocument document, LocalDate today) {
        Map<String, ExtractedField> extracted = new LinkedHashMap<>();
        if (document.fields() != null) {
            for (ExtractedField field : document.fields()) {
                extracted.putIfAbsent(field.field(), field);
            }
        }
        List<NormalizedField> fields = new ArrayList<>();
        List<ValidationIssue> validation = new ArrayList<>();
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        for (FieldDef definition : edition.fields()) {
            NormalizedField normalized = one(definition, extracted.get(definition.name()), today, validation);
            if (normalized == null) {
                continue;
            }
            fields.add(normalized);
            if ("VALID".equals(normalized.validationStatus()) && definition.type() == FieldValueType.DATE) {
                dates.put(definition.name(), LocalDate.parse(normalized.normalizedValue()));
            }
        }
        rules(edition, dates, today, validation);
        return new Result(List.copyOf(fields), List.copyOf(validation), identity(edition, document, fields));
    }

    private static NormalizedField one(
            FieldDef definition, ExtractedField extracted, LocalDate today, List<ValidationIssue> validation) {
        String raw = extracted == null ? null : extracted.value();
        if (raw == null || raw.isBlank()) {
            if (!definition.required()) {
                return null;
            }
            validation.add(new ValidationIssue("L2", "missing_expected_field", "WARNING", definition.name()));
            return new NormalizedField(definition.name(), raw, null, null, confidence(extracted), source(extracted), "MISSING");
        }
        return switch (definition.type()) {
            case DATE -> date(definition, extracted, raw, today, validation);
            case NAME -> name(definition, extracted, raw, validation);
            case SEX -> sex(definition, extracted, raw, validation);
            case COUNTRY, NATIONALITY -> country(definition, extracted, raw, validation);
            case DOCUMENT_NUMBER -> documentNumber(extracted, raw);
            case ADDRESS, STRING -> text(definition, extracted, raw);
            case POSTAL_CODE -> postal(definition, extracted, raw, validation);
            case MRZ -> textKey(extracted, raw, raw.toUpperCase(Locale.ROOT).replace(" ", ""));
        };
    }

    private static NormalizedField date(
            FieldDef definition, ExtractedField extracted, String raw, LocalDate today, List<ValidationIssue> validation) {
        Set<LocalDate> parsed = new LinkedHashSet<>();
        for (String format : formats(definition.formats())) {
            try {
                parsed.add(LocalDate.parse(
                        raw.trim(),
                        DateTimeFormatter.ofPattern(format.replace("yyyy", "uuuu"), Locale.ROOT)
                                .withResolverStyle(ResolverStyle.STRICT)));
            } catch (DateTimeParseException | IllegalArgumentException ignored) {
                // Un format qui ne s'applique pas n'est pas une erreur tant qu'un autre réussit.
            }
        }
        if (parsed.size() > 1) {
            validation.add(new ValidationIssue("L1", "ambiguous_date", "WARNING", definition.name()));
            return copy(definition.name(), extracted, raw, null, null, "AMBIGUOUS");
        }
        if (parsed.isEmpty() || ("dateOfBirth".equals(definition.name()) && parsed.iterator().next().isAfter(today))) {
            validation.add(new ValidationIssue("L1", "impossible_date", "ERROR", definition.name()));
            return copy(definition.name(), extracted, raw, null, null, "INVALID");
        }
        String iso = parsed.iterator().next().toString();
        return copy(definition.name(), extracted, raw, iso, iso, "VALID");
    }

    private static void rules(
            SchemaEdition edition, Map<String, LocalDate> dates, LocalDate today, List<ValidationIssue> validation) {
        for (RuleDef rule : edition.rules()) {
            var matcher = COMPARISON.matcher(rule.expression().trim());
            if (!matcher.matches()) {
                continue;
            }
            LocalDate left = dates.get(matcher.group(1));
            LocalDate right = dates.get(matcher.group(3));
            if (left == null || right == null || !compare(left, matcher.group(2), right)) {
                continue;
            }
            validation.add(new ValidationIssue(
                    rule.level().name(), rule.code(), rule.severity().name(), matcher.group(1)));
        }
        LocalDate expiration = dates.get("expirationDate");
        if (expiration != null && expiration.isBefore(today)) {
            validation.add(new ValidationIssue("L3", "document_expired", "ERROR", "expirationDate"));
        }
    }

    private static boolean compare(LocalDate left, String operator, LocalDate right) {
        return switch (operator) {
            case "<" -> left.isBefore(right);
            case ">" -> left.isAfter(right);
            case "<=" -> !left.isAfter(right);
            case ">=" -> !left.isBefore(right);
            default -> false;
        };
    }

    private static NormalizedField name(
            FieldDef definition, ExtractedField extracted, String raw, List<ValidationIssue> validation) {
        String display = collapse(raw);
        if (display.isBlank()) {
            if (definition.required()) {
                validation.add(new ValidationIssue("L2", "missing_expected_field", "WARNING", definition.name()));
                return copy(definition.name(), extracted, raw, null, null, "MISSING");
            }
            return null;
        }
        return copy(definition.name(), extracted, raw, display, comparisonKey(display), "VALID");
    }

    private static NormalizedField sex(
            FieldDef definition, ExtractedField extracted, String raw, List<ValidationIssue> validation) {
        String token = collapse(raw).toUpperCase(Locale.ROOT);
        String stripped = stripAccents(token);
        List<String> declared = formats(definition.formats());
        String canonical = SEX.get(stripped);
        boolean listed = declared.isEmpty() || declared.contains(token) || declared.contains(stripped);
        if (canonical == null || !listed) {
            validation.add(new ValidationIssue("L1", "invalid_sex", "ERROR", definition.name()));
            return copy(definition.name(), extracted, raw, null, null, "INVALID");
        }
        return copy(definition.name(), extracted, raw, canonical, canonical, "VALID");
    }

    private static NormalizedField country(
            FieldDef definition, ExtractedField extracted, String raw, List<ValidationIssue> validation) {
        String code = collapse(raw).toUpperCase(Locale.ROOT);
        if (code.length() == 3) {
            code = alpha2(code);
        }
        if (code == null || !ISO_COUNTRIES.contains(code)) {
            String issue = definition.type() == FieldValueType.NATIONALITY ? "invalid_nationality" : "invalid_country";
            validation.add(new ValidationIssue("L1", issue, "ERROR", definition.name()));
            return copy(definition.name(), extracted, raw, null, null, "INVALID");
        }
        String key = definition.type() == FieldValueType.NATIONALITY ? alpha3(code) : code;
        return copy(definition.name(), extracted, raw, code, key, "VALID");
    }

    private static NormalizedField documentNumber(ExtractedField extracted, String raw) {
        String display = collapse(raw);
        String key = display.toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
        return copy(extracted.field(), extracted, raw, display, key, "VALID");
    }

    private static NormalizedField postal(
            FieldDef definition, ExtractedField extracted, String raw, List<ValidationIssue> validation) {
        String display = collapse(raw).toUpperCase(Locale.ROOT);
        List<String> patterns = formats(definition.formats());
        if (!patterns.isEmpty() && patterns.stream().noneMatch(pattern -> display.matches(pattern))) {
            validation.add(new ValidationIssue("L1", "invalid_postal_code", "ERROR", definition.name()));
            return copy(definition.name(), extracted, raw, null, null, "INVALID");
        }
        return copy(definition.name(), extracted, raw, display, display.replace(" ", ""), "VALID");
    }

    private static NormalizedField text(FieldDef definition, ExtractedField extracted, String raw) {
        return copy(definition.name(), extracted, raw, collapse(raw), comparisonKey(collapse(raw)), "VALID");
    }

    private static NormalizedField textKey(ExtractedField extracted, String raw, String key) {
        return copy(extracted.field(), extracted, raw, key, key, "VALID");
    }

    private static NormalizedField copy(
            String name, ExtractedField extracted, String raw, String normalized, String key, String status) {
        return new NormalizedField(name, raw, normalized, key, confidence(extracted), source(extracted), status);
    }

    private static Map<String, Object> identity(
            SchemaEdition edition, ParsedDocument document, List<NormalizedField> fields) {
        Map<String, Object> identity = new LinkedHashMap<>();
        put(identity, "first_name", value(fields, "firstName"));
        put(identity, "last_name", value(fields, "lastName"));
        put(identity, "birth_date", value(fields, "dateOfBirth"));
        put(identity, "document_number", value(fields, "documentNumber"));
        put(identity, "expiration_date", value(fields, "expirationDate"));
        identity.put("document_type", edition.documentType().toLowerCase(Locale.ROOT));
        identity.put("document_country", edition.country());
        if (edition.issuingJurisdiction() != null) {
            identity.put("issuing_jurisdiction", edition.issuingJurisdiction());
        }
        identity.put("document_code", document.classification().code());
        identity.put("schema_version", edition.version());
        return Map.copyOf(identity);
    }

    private static void put(Map<String, Object> identity, String key, String value) {
        if (value != null) {
            identity.put(key, value);
        }
    }

    private static String value(List<NormalizedField> fields, String name) {
        for (NormalizedField field : fields) {
            if (name.equals(field.field()) && "VALID".equals(field.validationStatus())) {
                return field.normalizedValue();
            }
        }
        return null;
    }

    private static List<String> formats(String formats) {
        if (formats == null || formats.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String format : formats.split(",")) {
            if (!format.isBlank()) {
                values.add(format.trim());
            }
        }
        return values;
    }

    private static String collapse(String raw) {
        return SPACES.matcher(raw.trim()).replaceAll(" ");
    }

    private static String comparisonKey(String display) {
        return stripAccents(display).toUpperCase(Locale.ROOT);
    }

    private static String stripAccents(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder stripped = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            int type = Character.getType(decomposed.charAt(i));
            if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK) {
                stripped.append(decomposed.charAt(i));
            }
        }
        return stripped.toString();
    }

    private static String alpha2(String alpha3) {
        for (String country : ISO_COUNTRIES) {
            if (alpha3.equals(alpha3(country))) {
                return country;
            }
        }
        return null;
    }

    private static String alpha3(String alpha2) {
        return new Locale("", alpha2).getISO3Country();
    }

    private static Double confidence(ExtractedField extracted) {
        return extracted == null ? null : extracted.confidence();
    }

    private static String source(ExtractedField extracted) {
        return extracted == null ? null : extracted.source();
    }
}
