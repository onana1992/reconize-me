package com.kyc.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 10, max = 128) String password,
        @NotBlank @JsonProperty("organization_name") @Size(min = 2, max = 100) String organizationName,
        @JsonProperty("invite_token") @Size(max = 256) String inviteToken,
        @JsonProperty("first_name") @Size(max = 100) String firstName,
        @JsonProperty("last_name") @Size(max = 100) String lastName) {}
