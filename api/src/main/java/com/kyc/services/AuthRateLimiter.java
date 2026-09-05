package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {

    private final KycProperties properties;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AuthRateLimiter(KycProperties properties) {
        this.properties = properties;
    }

    public void check(String action, String ip) {
        String key = action + ":" + (ip == null || ip.isBlank() ? "unknown" : ip);
        long now = Instant.now().toEpochMilli();
        long cutoff = now - Duration.ofMinutes(properties.authRateWindowMinutes()).toMillis();
        Deque<Long> times = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.removeFirst();
            }
            if (times.size() >= properties.authRateLimit()) {
                throw ApiException.tooManyRequests();
            }
            times.addLast(now);
        }
    }
}
