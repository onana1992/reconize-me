package com.kyc.adapters;

import com.kyc.ports.ConsoleSessionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConsoleSessionStore implements ConsoleSessionStore {

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    @Override
    public void put(String sessionId, Session session, Duration ttl) {
        sessions.put(sessionId, new Entry(session, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<Session> find(String sessionId) {
        Entry entry = sessions.get(sessionId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(entry.session());
    }

    @Override
    public void delete(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().session().userId().equals(userId));
    }

    private record Entry(Session session, Instant expiresAt) {}
}
