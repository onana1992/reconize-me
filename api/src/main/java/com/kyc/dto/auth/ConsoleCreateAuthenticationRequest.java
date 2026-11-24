package com.kyc.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ConsoleCreateAuthenticationRequest(
        @JsonProperty("external_id") String externalId,
        @JsonProperty("integration_id") UUID integrationId) {}
