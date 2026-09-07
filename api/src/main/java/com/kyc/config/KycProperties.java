package com.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "kyc")
public record KycProperties(
        @DefaultValue("3600") int hostedUrlTtlSeconds,
        @DefaultValue("http://localhost:3001") String publicFlowBaseUrl,
        @DefaultValue("http://localhost:3000") String publicConsoleBaseUrl,
        @DefaultValue("consent-v1") String consentTextVersion,
        @DefaultValue("24") int idempotencyTtlHours,
        @DefaultValue("local-dev-pepper") String ipHashPepper,
        @DefaultValue("false") boolean sessionCookieSecure,
        @DefaultValue("5") int authRateLimit,
        @DefaultValue("15") int authRateWindowMinutes,
        @DefaultValue Mail mail) {

    public record Mail(
            @DefaultValue("log") String mode,
            @DefaultValue("Recogniz-Me <noreply@localhost>") String fromAddress,
            @DefaultValue("") String emailRedirectTo) {

        public String redirectTo() {
            return emailRedirectTo == null ? "" : emailRedirectTo.trim();
        }
    }

    public String hostedUrl(String token) {
        String base = publicFlowBaseUrl.endsWith("/")
                ? publicFlowBaseUrl.substring(0, publicFlowBaseUrl.length() - 1)
                : publicFlowBaseUrl;
        return base + "/flow/" + token;
    }

    public String consoleUrl(String path) {
        String base = publicConsoleBaseUrl.endsWith("/")
                ? publicConsoleBaseUrl.substring(0, publicConsoleBaseUrl.length() - 1)
                : publicConsoleBaseUrl;
        return path.startsWith("/") ? base + path : base + "/" + path;
    }
}
