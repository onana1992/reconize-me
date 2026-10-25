package com.kyc.services.aml;

import com.kyc.services.aml.DeterministicAmlEngine.SubjectInput;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class SubjectParser {

    private SubjectParser() {}

    public record Parsed(
            SubjectInput input, String countryOfResidence, String idType, String externalId) {}

    public static Parsed parse(
            String firstName,
            String middleName,
            String lastName,
            String dateOfBirth,
            String nationality,
            String countryOfResidence,
            String idNumber,
            String idType) {
        requireName("first_name", firstName);
        requireName("last_name", lastName);
        if (middleName != null && !middleName.isBlank() && (middleName.trim().length() > 128 || !hasLetter(middleName))) {
            throw new SubjectException("middle_name", "size");
        }
        LocalDate dob = null;
        if (dateOfBirth != null && !dateOfBirth.isBlank()) {
            try {
                dob = LocalDate.parse(dateOfBirth.trim());
            } catch (DateTimeParseException ex) {
                throw new SubjectException("date_of_birth", "invalid");
            }
            int year = dob.getYear();
            int current = LocalDate.now().getYear();
            if (year < 1900 || year > current) {
                throw new SubjectException("date_of_birth", "invalid");
            }
        }
        nationality = country("nationality", nationality);
        countryOfResidence = country("country_of_residence", countryOfResidence);
        if (idNumber != null && !idNumber.isBlank() && (idNumber.trim().length() > 64)) {
            throw new SubjectException("id_number", "size");
        }
        if (idType != null && !idType.isBlank() && !idType.equals("passport") && !idType.equals("national_id") && !idType.equals("other")) {
            throw new SubjectException("id_type", "invalid");
        }
        return new Parsed(
                new SubjectInput(
                        firstName.trim(),
                        blank(middleName),
                        lastName.trim(),
                        dob,
                        nationality,
                        blank(idNumber)),
                countryOfResidence,
                blank(idType),
                null);
    }

    private static void requireName(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new SubjectException(field, "required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 128 || !hasLetter(trimmed) || letterCount(trimmed) < 2) {
            throw new SubjectException(field, "size");
        }
    }

    private static String country(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (code.length() != 2 || !code.chars().allMatch(Character::isLetter)) {
            throw new SubjectException(field, "invalid");
        }
        return code;
    }

    private static boolean hasLetter(String value) {
        return value.codePoints().anyMatch(Character::isLetter);
    }

    private static int letterCount(String value) {
        return (int) value.codePoints().filter(Character::isLetter).count();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class SubjectException extends IllegalArgumentException {
        private final String field;
        private final String code;

        public SubjectException(String field, String code) {
            super(field);
            this.field = field;
            this.code = code;
        }

        public String field() {
            return field;
        }

        public String code() {
            return code;
        }
    }
}
