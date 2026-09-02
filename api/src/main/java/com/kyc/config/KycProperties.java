package com.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "kyc")
public record KycProperties(
        @DefaultValue("3600") int hostedUrlTtlSeconds,
        @DefaultValue("http://localhost:3001") String publicFlowBaseUrl,
        @DefaultValue("consent-v1") String consentTextVersion,
        @DefaultValue("24") int idempotencyTtlHours,
        @DefaultValue("local-dev-pepper") String ipHashPepper) {

    public String hostedUrl(String token) {
        String base = publicFlowBaseUrl.endsWith("/")
                ? publicFlowBaseUrl.substring(0, publicFlowBaseUrl.length() - 1)
                : publicFlowBaseUrl;
        return base + "/flow/" + token;
    }
}
