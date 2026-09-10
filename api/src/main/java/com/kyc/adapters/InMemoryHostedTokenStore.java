package com.kyc.adapters;

import com.kyc.ports.HostedTokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHostedTokenStore implements HostedTokenStore {

    private final Map<String, Held> byToken = new ConcurrentHashMap<>();
    private final Map<UUID, String> byVerification = new ConcurrentHashMap<>();

    @Override
    public void put(String token, UUID organizationId, UUID verificationId, Duration ttl) {
        byToken.put(token, new Held(new Entry(organizationId, verificationId), Instant.now().plus(ttl)));
        byVerification.put(verificationId, token);
    }

    @Override
    public Optional<Entry> get(String token) {
        Held held = byToken.get(token);
        if (held == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(held.expiresAt())) {
            revokeByVerificationId(held.entry().verificationId());
            return Optional.empty();
        }
        return Optional.of(held.entry());
    }

    @Override
    public Optional<String> tokenFor(UUID verificationId) {
        String token = byVerification.get(verificationId);
        if (token == null) {
            return Optional.empty();
        }
        return get(token).isPresent() ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void revokeByVerificationId(UUID verificationId) {
        String token = byVerification.remove(verificationId);
        if (token != null) {
            byToken.remove(token);
        }
    }

    private record Held(Entry entry, Instant expiresAt) {}
}
