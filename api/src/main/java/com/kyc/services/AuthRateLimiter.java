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

    /** Rejects if the window is full, then records this call (resend, forgot). */
    public void check(String action, String ip) {
        visit(action, ip, true);
    }

    /** Rejects if the window is full. Does not record this call (login). */
    public void checkAllowed(String action, String ip) {
        visit(action, ip, false);
    }

    /** Records a failed login. Does not throw. */
    public void recordFailure(String action, String ip) {
        Deque<Long> times = bucket(action, ip);
        long now = Instant.now().toEpochMilli();
        synchronized (times) {
            prune(times, now);
            times.addLast(now);
        }
    }

    private void visit(String action, String ip, boolean record) {
        Deque<Long> times = bucket(action, ip);
        long now = Instant.now().toEpochMilli();
        synchronized (times) {
            prune(times, now);
            if (times.size() >= properties.authRateLimit()) {
                throw ApiException.tooManyRequests();
            }
            if (record) {
                times.addLast(now);
            }
        }
    }

    private void prune(Deque<Long> times, long now) {
        long cutoff = now - Duration.ofMinutes(properties.authRateWindowMinutes()).toMillis();
        while (!times.isEmpty() && times.peekFirst() < cutoff) {
            times.removeFirst();
        }
    }

    private Deque<Long> bucket(String action, String ip) {
        String key = action + ":" + (ip == null || ip.isBlank() ? "unknown" : ip);
        return hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    }
}
