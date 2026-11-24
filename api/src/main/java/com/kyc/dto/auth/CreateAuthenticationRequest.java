package com.kyc.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record CreateAuthenticationRequest(
        @JsonProperty("external_id") String externalId,
        Map<String, Object> metadata,
        @JsonProperty("sandbox_scenario") Object sandboxScenario) {}
