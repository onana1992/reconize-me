package com.kyc.ports;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ConsoleSessionStore {

    void put(String sessionId, Session session, Duration ttl);

    Optional<Session> find(String sessionId);

    void delete(String sessionId);

    record Session(UUID userId, UUID organizationId, String role) {}
}
