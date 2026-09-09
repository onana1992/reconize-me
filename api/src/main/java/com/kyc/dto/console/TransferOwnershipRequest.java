package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferOwnershipRequest(@NotNull @JsonProperty("user_id") UUID userId) {}
