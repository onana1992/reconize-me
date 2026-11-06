package com.kyc.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record AntiSpoof(
        @DefaultValue("false") boolean labEnabled,
        @DefaultValue("http://127.0.0.1:8090") String baseUrl,
        @DefaultValue("") String token,
        @DefaultValue("35000") int timeoutMs,
        @DefaultValue("0.80") double declineAt,
        @DefaultValue("0.55") double reviewAt,
        @DefaultValue("") String declineCodes) {

    public Set<String> openDeclineCodes() {
        Set<String> codes = new LinkedHashSet<>();
        if (declineCodes == null || declineCodes.isBlank()) {
            return Set.of();
        }
        for (String part : declineCodes.split(",")) {
            String code = part.trim();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return Set.copyOf(codes);
    }
}
