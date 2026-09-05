package com.kyc.security;

import java.util.UUID;

public record ConsolePrincipal(UUID userId, UUID organizationId, String role) {

    public boolean owner() {
        return "owner".equals(role);
    }
}
