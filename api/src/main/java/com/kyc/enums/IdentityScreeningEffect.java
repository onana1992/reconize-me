package com.kyc.enums;

public enum IdentityScreeningEffect {
    IGNORE,
    REVIEW,
    BLOCK;

    public String json() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static IdentityScreeningEffect parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
