package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record CreateVerificationRequest(
        @JsonProperty("external_id") String externalId, Applicant applicant, Map<String, Object> metadata) {

    public record Applicant(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email) {}
}
