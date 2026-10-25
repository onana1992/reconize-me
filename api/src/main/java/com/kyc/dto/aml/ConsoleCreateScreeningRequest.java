package com.kyc.dto.aml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ConsoleCreateScreeningRequest(
        @JsonProperty("integration_id") UUID integrationId,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("date_of_birth") String dateOfBirth,
        String nationality) {}
