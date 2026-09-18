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
        @DefaultValue Mail mail,
        @DefaultValue Billing billing,
        @DefaultValue Stripe stripe) {

    public record Mail(
            @DefaultValue("log") String mode,
            @DefaultValue("Recogniz-Me <noreply@localhost>") String fromAddress,
            @DefaultValue("") String emailRedirectTo) {

        public String redirectTo() {
            return emailRedirectTo == null ? "" : emailRedirectTo.trim();
        }
    }

    public record Billing(
            @DefaultValue("usd") String currency,
            @DefaultValue("900") long unitAmountMinor) {

        public static final java.util.List<Long> PACKS = java.util.List.of(5000L, 10000L, 25000L, 50000L);

        public java.util.List<Long> packs() {
            return PACKS;
        }

        public boolean isAllowedPack(long packMinor) {
            return PACKS.contains(packMinor);
        }
    }

    public record Stripe(
            @DefaultValue("log") String mode,
            @DefaultValue("") String secretKey,
            @DefaultValue("whsec_test") String webhookSecret) {

        public boolean usesLiveApi() {
            return "stripe".equalsIgnoreCase(mode);
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
