package com.kyc.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AcceptInviteRequest(@NotBlank @Size(max = 256) String token) {}
