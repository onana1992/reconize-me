package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kyc.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangePasswordRequest(
        @NotBlank @JsonProperty("current_password") @Size(max = 128) String currentPassword,
        @StrongPassword @JsonProperty("new_password") String newPassword) {}
