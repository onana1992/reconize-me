package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.ports.ConsoleSessionStore;
import com.kyc.ports.ConsoleSessionStore.Session;
import com.kyc.security.ConsolePrincipal;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    public static final String COOKIE_NAME = "rm_session";
    private static final Duration TTL = Duration.ofDays(7);

    private final ConsoleSessionStore store;
    private final KycProperties properties;

    public SessionService(ConsoleSessionStore store, KycProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public String create(ConsolePrincipal principal) {
        String raw = CryptoTokens.randomHostedToken();
        store.put(hash(raw), new Session(principal.userId(), principal.organizationId(), principal.role()), TTL);
        return raw;
    }

    public Optional<ConsolePrincipal> authenticate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return store.find(hash(raw))
                .map(session -> new ConsolePrincipal(session.userId(), session.organizationId(), session.role()));
    }

    public void invalidate(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        store.delete(hash(raw));
    }

    public ResponseCookie cookie(String raw) {
        return ResponseCookie.from(COOKIE_NAME, raw)
                .httpOnly(true)
                .path("/")
                .maxAge(TTL)
                .sameSite("Lax")
                .secure(properties.sessionCookieSecure())
                .build();
    }

    public ResponseCookie expiredCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .secure(properties.sessionCookieSecure())
                .build();
    }

    public static void write(jakarta.servlet.http.HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String hash(String raw) {
        return CryptoTokens.sha256HexPeppered(properties.ipHashPepper(), raw);
    }
}
