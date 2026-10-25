package com.kyc.dto.aml;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateScreeningRequest(
        String type,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("middle_name") String middleName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("date_of_birth") String dateOfBirth,
        String nationality,
        @JsonProperty("country_of_residence") String countryOfResidence,
        @JsonProperty("id_number") String idNumber,
        @JsonProperty("id_type") String idType,
        @JsonProperty("external_id") String externalId) {}
