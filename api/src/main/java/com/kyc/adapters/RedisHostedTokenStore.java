package com.kyc.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.enums.ProductCode;
import com.kyc.ports.HostedTokenStore;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisHostedTokenStore implements HostedTokenStore {

    private static final String PREFIX = "hosted:v1:";
    private static final String VID_PREFIX = "hosted:vid:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisHostedTokenStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(String token, UUID organizationId, UUID resourceId, ProductCode product, Duration ttl) {
        try {
            redis.opsForValue()
                    .set(
                            PREFIX + token,
                            objectMapper.writeValueAsString(new Entry(organizationId, resourceId, product)),
                            ttl);
            redis.opsForValue().set(VID_PREFIX + resourceId, token, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize hosted token", e);
        }
    }

    @Override
    public Optional<Entry> get(String token) {
        try {
            String json = redis.opsForValue().get(PREFIX + token);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, Entry.class));
        } catch (RuntimeException | JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> tokenFor(UUID resourceId) {
        return Optional.ofNullable(redis.opsForValue().get(VID_PREFIX + resourceId));
    }

    @Override
    public void revokeByVerificationId(UUID resourceId) {
        String token = redis.opsForValue().get(VID_PREFIX + resourceId);
        redis.delete(VID_PREFIX + resourceId);
        if (token != null) {
            redis.delete(PREFIX + token);
        }
    }
}
