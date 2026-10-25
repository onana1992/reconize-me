package com.kyc.enums;

public enum AmlCategory {
    SANCTION,
    DEBARMENT,
    CRIME,
    PEP,
    RCA;

    public String json() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static AmlCategory parse(String raw) {
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
