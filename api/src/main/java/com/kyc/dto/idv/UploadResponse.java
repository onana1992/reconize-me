package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record UploadResponse(
        @JsonProperty("upload_url") String uploadUrl,
        @JsonProperty("object_key") String objectKey,
        @JsonProperty("expires_at") Instant expiresAt,
        int attempt) {}
