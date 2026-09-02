package com.kyc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateVerificationRequest(
        @JsonProperty("external_id")
                @Size(min = 1, max = 128)
                @Pattern(regexp = "[A-Za-z0-9._:-]+")
                String externalId,
        @Valid ApplicantRequest applicant,
        Map<String, Object> metadata) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApplicantRequest(
            @JsonProperty("first_name") @Size(min = 1, max = 100) String firstName,
            @JsonProperty("last_name") @Size(min = 1, max = 100) String lastName,
            @Email @Size(max = 255) String email) {}
}
