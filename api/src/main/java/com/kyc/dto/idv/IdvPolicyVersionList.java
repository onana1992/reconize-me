package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record IdvPolicyVersionList(List<Item> versions) {

    public record Item(
            int version,
            @JsonProperty("published_at") Instant publishedAt,
            boolean active) {}
}
