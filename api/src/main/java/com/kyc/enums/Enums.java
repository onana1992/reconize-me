package com.kyc.enums;

import java.util.Locale;

public final class Enums {

    private Enums() {}

    public static String json(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
