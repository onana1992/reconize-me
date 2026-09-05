package com.kyc.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record IssuedApiKeyResponse(UUID id, String key, @JsonProperty("key_prefix") String keyPrefix) {}
