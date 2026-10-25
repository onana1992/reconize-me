package com.kyc.services.aml;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AmlNames {

    private AmlNames() {}

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String stripped = Normalizer.normalize(raw, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        String upper = stripped.toUpperCase(Locale.ROOT);
        String spaced = upper.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll(" +", " ");
        return spaced;
    }

    public static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String stripped = Normalizer.normalize(raw, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        return stripped.toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static String queryName(String first, String middle, String last) {
        List<String> parts = new ArrayList<>();
        add(parts, normalize(first));
        add(parts, normalize(middle));
        add(parts, normalize(last));
        return String.join(" ", parts);
    }

    static List<String> tokens(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split(" "));
    }

    static int levenshtein(String left, String right) {
        int n = left.length();
        int m = right.length();
        int[] row = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            row[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            int previous = row[0];
            row[0] = i;
            for (int j = 1; j <= m; j++) {
                int current = row[j];
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), previous + cost);
                previous = current;
            }
        }
        return row[m];
    }

    private static void add(List<String> parts, String value) {
        if (!value.isEmpty()) {
            parts.add(value);
        }
    }
}
