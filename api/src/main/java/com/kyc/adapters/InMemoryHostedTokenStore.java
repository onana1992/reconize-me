package com.kyc.adapters;

import com.kyc.ports.HostedTokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHostedTokenStore implements HostedTokenStore {

    private final Map<String, Entry> byToken = new ConcurrentHashMap<>();
    private final Map<UUID, String> byVerification = new ConcurrentHashMap<>();

    @Override
    public void put(String token, HostedSession session, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        byToken.put(token, new Entry(session, expiresAt));
        byVerification.put(session.verificationId(), token);
    }

    @Override
    public Optional<HostedSession> findSession(String token) {
        Entry entry = byToken.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            delete(entry.session().verificationId());
            return Optional.empty();
        }
        return Optional.of(entry.session());
    }

    @Override
    public Optional<String> findToken(UUID verificationId) {
        String token = byVerification.get(verificationId);
        if (token == null) {
            return Optional.empty();
        }
        return findSession(token).map(session -> token);
    }

    @Override
    public void delete(UUID verificationId) {
        String token = byVerification.remove(verificationId);
        if (token != null) {
            byToken.remove(token);
        }
    }

    private record Entry(HostedSession session, Instant expiresAt) {}
}
