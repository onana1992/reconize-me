package com.kyc.security;

import com.kyc.web.ApiException;
import java.util.Comparator;
import java.util.List;

public final class ConsoleAuth {

    private ConsoleAuth() {}

    public static void require(ConsolePrincipal principal, Permission permission) {
        if (!allows(principal.role(), permission)) {
            throw ApiException.forbidden("forbidden", "Insufficient permissions");
        }
    }

    public static boolean allows(String role, Permission permission) {
        try {
            return Permission.forRole(ConsoleRole.parse(role)).contains(permission);
        } catch (ApiException ex) {
            return false;
        }
    }

    public static List<String> permissionNames(String role) {
        return Permission.forRole(ConsoleRole.parse(role)).stream()
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
