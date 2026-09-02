package com.kyc.ports;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface HostedTokenStore {

    void put(String token, HostedSession session, Duration ttl);

    Optional<HostedSession> findSession(String token);

    Optional<String> findToken(UUID verificationId);

    void delete(UUID verificationId);

    record HostedSession(
            @JsonProperty("verification_id") UUID verificationId,
            @JsonProperty("organization_id") UUID organizationId,
            @JsonProperty("status_at_issue") String statusAtIssue) {}
}
