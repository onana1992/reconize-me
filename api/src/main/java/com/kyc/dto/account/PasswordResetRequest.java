package com.kyc.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordResetRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Size(min = 10, max = 128) String password) {}
