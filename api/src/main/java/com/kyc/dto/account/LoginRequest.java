package com.kyc.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 128) String password) {}
