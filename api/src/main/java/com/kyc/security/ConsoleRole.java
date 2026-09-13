package com.kyc.security;

import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.util.List;
import java.util.Locale;

public enum ConsoleRole {
    OWNER("owner"),
    ADMIN("admin"),
    MEMBER("member"),
    READONLY("readonly"),
    DEVELOPER("developer");

    private final String value;

    ConsoleRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String frenchLabel() {
        return switch (this) {
            case OWNER -> "propriétaire";
            case ADMIN -> "administrateur";
            case MEMBER -> "membre";
            case READONLY -> "lecture seule";
            case DEVELOPER -> "développeur";
        };
    }

    public boolean matches(String raw) {
        return value.equals(raw);
    }

    public static ConsoleRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ConsoleRole role : values()) {
            if (role.value.equals(normalized)) {
                return role;
            }
        }
        throw invalid();
    }

    public static ConsoleRole parseOrDefault(String raw, ConsoleRole fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return parse(raw);
    }

    public static ConsoleRole requireInvitable(String raw) {
        ConsoleRole role = parseOrDefault(raw, MEMBER);
        if (role == OWNER) {
            throw invalid();
        }
        return role;
    }

    public static String fromInvite(String raw) {
        ConsoleRole role = parseOrDefault(raw, MEMBER);
        return role == OWNER ? MEMBER.value : role.value;
    }

    private static ApiException invalid() {
        return ApiException.validation("Request validation failed", List.of(new ErrorDetail("role", "invalid")));
    }
}
