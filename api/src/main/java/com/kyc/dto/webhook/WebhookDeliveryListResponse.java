package com.kyc.dto.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookDeliveryListResponse(List<Item> deliveries) {

    public record Item(
            @JsonProperty("event_id") UUID eventId,
            @JsonProperty("verification_id") UUID verificationId,
            String status,
            int attempt,
            @JsonProperty("http_status") Integer httpStatus,
            @JsonProperty("created_at") Instant createdAt) {}
}
