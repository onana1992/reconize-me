package com.kyc.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentConsole {

    private CurrentConsole() {}

    public static ConsolePrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ConsolePrincipal principal)) {
            throw new IllegalStateException("Console session required");
        }
        return principal;
    }
}
