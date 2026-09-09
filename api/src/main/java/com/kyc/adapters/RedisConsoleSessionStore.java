package com.kyc.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.ConsoleSessionStore;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisConsoleSessionStore implements ConsoleSessionStore {

    private static final String PREFIX = "console:session:";
    private static final String USER_PREFIX = "console:user-sessions:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisConsoleSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(String sessionId, Session session, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + sessionId, objectMapper.writeValueAsString(session), ttl);
            String userKey = USER_PREFIX + session.userId();
            redis.opsForSet().add(userKey, sessionId);
            Long remaining = redis.getExpire(userKey);
            if (remaining == null || remaining < ttl.toSeconds()) {
                redis.expire(userKey, ttl);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize console session", e);
        }
    }

    @Override
    public Optional<Session> find(String sessionId) {
        try {
            String json = redis.opsForValue().get(PREFIX + sessionId);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, Session.class));
        } catch (RuntimeException | JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String sessionId) {
        String json = redis.opsForValue().get(PREFIX + sessionId);
        redis.delete(PREFIX + sessionId);
        if (json == null) {
            return;
        }
        try {
            Session session = objectMapper.readValue(json, Session.class);
            redis.opsForSet().remove(USER_PREFIX + session.userId(), sessionId);
        } catch (JsonProcessingException ignored) {
            // Index cleanup is best-effort; membership re-read still rejects the cookie.
        }
    }

    @Override
    public void deleteByUserId(UUID userId) {
        String userKey = USER_PREFIX + userId;
        Set<String> ids = redis.opsForSet().members(userKey);
        if (ids != null) {
            for (String id : ids) {
                redis.delete(PREFIX + id);
            }
        }
        redis.delete(userKey);
    }
}
