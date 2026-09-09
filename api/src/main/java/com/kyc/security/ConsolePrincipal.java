package com.kyc.security;

import java.util.UUID;

public record ConsolePrincipal(UUID userId, UUID organizationId, String role) {

    public boolean owner() {
        return ConsoleRole.OWNER.matches(role);
    }

    public boolean has(Permission permission) {
        return ConsoleAuth.allows(role, permission);
    }
}
