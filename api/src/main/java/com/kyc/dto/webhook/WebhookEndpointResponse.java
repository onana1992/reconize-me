package com.kyc.dto.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookEndpointResponse(
        String url,
        @JsonProperty("secret_prefix") String secretPrefix,
        String secret,
        String status) {}
