package com.kyc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsentRequest(
        @NotBlank @Pattern(regexp = "accepted|declined") String decision) {}
