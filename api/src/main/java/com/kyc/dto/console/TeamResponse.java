package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeamResponse(List<MemberItem> members, List<InviteItem> invites) {

    public record MemberItem(
            UUID id,
            String email,
            String role,
            String status,
            @JsonProperty("created_at") Instant createdAt) {}

    public record InviteItem(
            UUID id, String email, String role, @JsonProperty("expires_at") Instant expiresAt) {}
}
