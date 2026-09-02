package com.kyc.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.HostedTokenStore;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisHostedTokenStore implements HostedTokenStore {

    private static final String TOKEN_PREFIX = "hosted:v1:";
    private static final String VERIFICATION_PREFIX = "hosted:vid:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisHostedTokenStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(String token, HostedSession session, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redis.opsForValue().set(TOKEN_PREFIX + token, json, ttl);
            redis.opsForValue().set(VERIFICATION_PREFIX + session.verificationId(), token, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize hosted session", e);
        } catch (RuntimeException e) {
            throw e;
        }
    }

    @Override
    public Optional<HostedSession> findSession(String token) {
        try {
            String json = redis.opsForValue().get(TOKEN_PREFIX + token);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, HostedSession.class));
        } catch (RuntimeException | JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findToken(UUID verificationId) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(VERIFICATION_PREFIX + verificationId));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(UUID verificationId) {
        String token = redis.opsForValue().get(VERIFICATION_PREFIX + verificationId);
        redis.delete(VERIFICATION_PREFIX + verificationId);
        if (token != null) {
            redis.delete(TOKEN_PREFIX + token);
        }
    }
}
