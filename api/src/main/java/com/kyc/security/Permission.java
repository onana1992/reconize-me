package com.kyc.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum Permission {
    TEAM_READ,
    TEAM_WRITE,
    VERIFICATION_READ,
    VERIFICATION_WRITE,
    API_KEY_READ,
    API_KEY_WRITE,
    BILLING_READ,
    BILLING_WRITE,
    OWNERSHIP,
    AUDIT_READ;

    private static final Set<Permission> READONLY = Set.of(TEAM_READ, VERIFICATION_READ);
    private static final Set<Permission> MEMBER = Set.of(TEAM_READ, VERIFICATION_READ, VERIFICATION_WRITE);
    private static final Set<Permission> DEVELOPER = Set.of(TEAM_READ, API_KEY_READ, API_KEY_WRITE);
    private static final Set<Permission> ADMIN = Set.of(
            TEAM_READ,
            TEAM_WRITE,
            VERIFICATION_READ,
            VERIFICATION_WRITE,
            API_KEY_READ,
            API_KEY_WRITE,
            AUDIT_READ);
    private static final Set<Permission> OWNER = Collections.unmodifiableSet(EnumSet.allOf(Permission.class));

    public static Set<Permission> forRole(ConsoleRole role) {
        return switch (role) {
            case READONLY -> READONLY;
            case MEMBER -> MEMBER;
            case DEVELOPER -> DEVELOPER;
            case ADMIN -> ADMIN;
            case OWNER -> OWNER;
        };
    }
}
