package com.kyc.services;

import java.util.Locale;
import java.util.function.Predicate;

public final class OrganizationSlugs {

    private OrganizationSlugs() {}

    public static String fromName(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() < 3) {
            slug = (slug + "org").replaceAll("^-+", "");
            if (slug.length() < 3) {
                slug = "org";
            }
        }
        if (slug.length() > 64) {
            slug = slug.substring(0, 64).replaceAll("-+$", "");
        }
        if (slug.length() < 3) {
            slug = "org";
        }
        return slug;
    }

    public static String unique(String base, Predicate<String> taken) {
        if (!taken.test(base)) {
            return base;
        }
        for (int i = 2; i < 10_000; i++) {
            String suffix = "-" + i;
            String candidate = base;
            int maxBase = 64 - suffix.length();
            if (candidate.length() > maxBase) {
                candidate = candidate.substring(0, maxBase).replaceAll("-+$", "");
            }
            candidate = candidate + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate organization slug");
    }
}
