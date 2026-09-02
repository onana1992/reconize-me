package com.kyc.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentApiKey {

    private CurrentApiKey() {}

    public static ApiPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiPrincipal principal)) {
            throw new IllegalStateException("API key authentication required");
        }
        return principal;
    }
}
