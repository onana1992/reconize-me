package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchMemberRoleRequest(@NotBlank @Size(max = 16) String role) {}
