package com.kyc.enums;

public enum AmlOnMatch {
    IGNORE,
    REVIEW,
    BLOCK;

    public String json() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static AmlOnMatch parse(String raw) {
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
