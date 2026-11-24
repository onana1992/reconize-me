package com.kyc.ports;

import com.kyc.enums.ProductCode;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived tokens that bind a hosted URL to an organization resource (identity verification or
 * authentication session).
 */
public interface HostedTokenStore {

    /**
     * @param organizationId organization that owns the resource
     * @param verificationId resource id (verification or authentication session)
     * @param product IDENTITY or AUTHENTICATION (null treated as IDENTITY for legacy Redis rows)
     */
    record Entry(UUID organizationId, UUID verificationId, ProductCode product) {
        public Entry {
            if (product == null) {
                product = ProductCode.IDENTITY;
            }
        }

        public Entry(UUID organizationId, UUID verificationId) {
            this(organizationId, verificationId, ProductCode.IDENTITY);
        }

        public boolean authentication() {
            return product == ProductCode.AUTHENTICATION;
        }
    }

    void put(String token, UUID organizationId, UUID resourceId, ProductCode product, Duration ttl);

    default void put(String token, UUID organizationId, UUID verificationId, Duration ttl) {
        put(token, organizationId, verificationId, ProductCode.IDENTITY, ttl);
    }

    Optional<Entry> get(String token);

    Optional<String> tokenFor(UUID resourceId);

    void revokeByVerificationId(UUID resourceId);
}
