package com.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "kyc")
public record KycProperties(
        @DefaultValue("http://localhost:3000") String publicConsoleBaseUrl,
        @DefaultValue("http://localhost:3001") String publicFlowBaseUrl,
        @DefaultValue("3600") int hostedUrlTtlSeconds,
        @DefaultValue("consent-v1") String consentTextVersion,
        @DefaultValue("24") int idempotencyTtlHours,
        @DefaultValue("./data/media") String objectStorageRoot,
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

    public String consoleUrl(String path) {
        return join(publicConsoleBaseUrl, path);
    }

    public String hostedUrl(String token) {
        return join(publicFlowBaseUrl, "/flow/" + token);
    }

    private static String join(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return path.startsWith("/") ? base + path : base + "/" + path;
    }
}
