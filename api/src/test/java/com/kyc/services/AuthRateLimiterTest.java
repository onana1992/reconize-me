package com.kyc.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kyc.config.KycProperties;
import com.kyc.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthRateLimiterTest {

    private final AuthRateLimiter limiter = new AuthRateLimiter(properties(3));

    @Test
    void checkCountsEveryCall() {
        limiter.check("forgot", "10.0.0.1");
        limiter.check("forgot", "10.0.0.1");
        limiter.check("forgot", "10.0.0.1");
        assertThatThrownBy(() -> limiter.check("forgot", "10.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).status())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void loginSuccessDoesNotConsumeBudget() {
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> limiter.checkAllowed("login", "10.0.0.2")).doesNotThrowAnyException();
        }
    }

    @Test
    void onlyFailedLoginsConsumeBudget() {
        limiter.checkAllowed("login", "10.0.0.3");
        limiter.recordFailure("login", "10.0.0.3");
        limiter.checkAllowed("login", "10.0.0.3");
        limiter.recordFailure("login", "10.0.0.3");
        limiter.checkAllowed("login", "10.0.0.3");
        limiter.recordFailure("login", "10.0.0.3");

        assertThatThrownBy(() -> limiter.checkAllowed("login", "10.0.0.3"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).status())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void otherIpIsIndependent() {
        limiter.recordFailure("login", "10.0.0.4");
        limiter.recordFailure("login", "10.0.0.4");
        limiter.recordFailure("login", "10.0.0.4");
        assertThatCode(() -> limiter.checkAllowed("login", "10.0.0.5")).doesNotThrowAnyException();
    }

    private static KycProperties properties(int limit) {
        return new KycProperties(
                "http://localhost:3000",
                "http://localhost:3001",
                3600,
                "consent-v1",
                24,
                "./data/media",
                "pepper",
                false,
                limit,
                15,
                new KycProperties.Mail("log", "noreply@localhost", ""));
    }
}
