package com.kyc.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.ConsoleSessionStore;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisConsoleSessionStore implements ConsoleSessionStore {

    private static final String PREFIX = "console:session:";

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
        redis.delete(PREFIX + sessionId);
    }
}
