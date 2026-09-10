package com.kyc.ports;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface HostedTokenStore {

    record Entry(UUID organizationId, UUID verificationId) {}

    void put(String token, UUID organizationId, UUID verificationId, Duration ttl);

    Optional<Entry> get(String token);

    Optional<String> tokenFor(UUID verificationId);

    void revokeByVerificationId(UUID verificationId);
}
