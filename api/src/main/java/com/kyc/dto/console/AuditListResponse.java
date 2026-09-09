package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditListResponse(List<AuditEventItem> events, @JsonProperty("next_cursor") String nextCursor) {

    public record AuditEventItem(
            long id,
            String action,
            @JsonProperty("actor_type") String actorType,
            @JsonProperty("actor_id") UUID actorId,
            @JsonProperty("resource_type") String resourceType,
            @JsonProperty("resource_id") UUID resourceId,
            JsonNode payload,
            @JsonProperty("created_at") Instant createdAt) {}
}
