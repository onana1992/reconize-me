package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LivenessCompleteRequest(@JsonProperty("session_id") String sessionId) {}
