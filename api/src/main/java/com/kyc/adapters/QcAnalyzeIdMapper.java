package com.kyc.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.DocumentAiPort.DocumentSignals;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mapping figé D1 — permis QC recto uniquement. Voir docs/MVP/mapping-analyzeid-qc.md.
 */
public final class QcAnalyzeIdMapper {

    public static final String PROVIDER = "textract_analyze_id";

    private static final Set<String> DRIVER_LICENSE_MARKERS = Set.of(
            "DRIVER LICENSE", "DRIVER'S LICENSE", "DRIVERS LICENSE", "PERMIS DE CONDUIRE");
    private static final Set<String> CANADA_MARKERS = Set.of("CA", "CAN", "CANADA");
    private static final Set<String> QUEBEC_MARKERS = Set.of("QC", "QUE", "QUE.", "QUEBEC", "QUÉBEC", "QUEBEC.");

    private final Clock clock;

    public QcAnalyzeIdMapper() {
        this(Clock.systemUTC());
    }

    public QcAnalyzeIdMapper(Clock clock) {
        this.clock = clock;
    }

    public DocumentSignals map(JsonNode analyzeIdResponse) {
        Map<String, String> fields = indexFields(analyzeIdResponse);
        String idType = upper(fields.get("ID_TYPE"));
        if (idType == null || idType.isBlank()) {
            idType = upper(fields.get("DOCUMENT_TYPE"));
        }
        String country = upper(fields.get("COUNTRY"));
        String state = upper(fields.get("STATE_NAME"));
        if (state == null || state.isBlank()) {
            state = upper(fields.get("STATE_IN_ISO"));
        }
        if (state == null || state.isBlank()) {
            state = upper(fields.get("PLACE_OF_ISSUE"));
        }

        boolean driverLicense = isDriverLicense(idType);
        boolean canada = isCanada(country) || (country == null && isQuebec(state));
        boolean quebec = isQuebec(state);
        boolean supported = driverLicense && canada && quebec;

        String firstName = trim(fields.get("FIRST_NAME"));
        String lastName = trim(fields.get("LAST_NAME"));
        LocalDate birthDate = parseDate(fields.get("DATE_OF_BIRTH"));
        LocalDate expirationDate = parseDate(fields.get("EXPIRATION_DATE"));
        String documentNumber = trim(fields.get("DOCUMENT_NUMBER"));

        if (!supported) {
            return new DocumentSignals(
                    "unknown", "ZZ", null, false, false, false, null, null, null, null, null, PROVIDER);
        }

        boolean expired = expirationDate != null && expirationDate.isBefore(LocalDate.now(clock));
        return new DocumentSignals(
                "driving_license",
                "CA",
                "QC",
                expired,
                true,
                false,
                firstName,
                lastName,
                birthDate,
                documentNumber,
                expirationDate,
                PROVIDER);
    }

    private static Map<String, String> indexFields(JsonNode root) {
        Map<String, String> out = new HashMap<>();
        if (root == null) {
            return out;
        }
        JsonNode docs = root.get("IdentityDocuments");
        if (docs == null || !docs.isArray()) {
            return out;
        }
        for (JsonNode doc : docs) {
            JsonNode fields = doc.get("IdentityDocumentFields");
            if (fields == null || !fields.isArray()) {
                continue;
            }
            for (JsonNode field : fields) {
                String type = text(field.path("Type").path("Text"));
                String value = text(field.path("ValueDetection").path("Text"));
                if (type != null && !type.isBlank() && value != null && !value.isBlank()) {
                    out.putIfAbsent(type.trim().toUpperCase(Locale.ROOT), value.trim());
                }
            }
        }
        return out;
    }

    private static boolean isDriverLicense(String value) {
        if (value == null) {
            return false;
        }
        for (String marker : DRIVER_LICENSE_MARKERS) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCanada(String value) {
        return value != null && CANADA_MARKERS.contains(value);
    }

    private static boolean isQuebec(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.replace('É', 'E').replace('È', 'E');
        return QUEBEC_MARKERS.contains(value) || QUEBEC_MARKERS.contains(normalized);
    }

    private static LocalDate parseDate(String raw) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
        }) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
