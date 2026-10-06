package com.kyc.ports;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Store for managing short-lived tokens that link an organization and verification operation
 * to a token, used (for example) when constructing hosted URLs for a verification flow.
 */
public interface HostedTokenStore {

    /**
     * Represents an entry mapping a token to an organization and verification.
     *
     * @param organizationId  The unique identifier of the organization.
     * @param verificationId  The unique identifier of the verification operation.
     */
    record Entry(UUID organizationId, UUID verificationId) {}

    /**
     * Adds or updates a mapping from the given token to the specified organization and verification,
     * with a time-to-live after which the mapping expires.
     *
     * @param token           The ephemeral token to store.
     * @param organizationId  The organization's UUID.
     * @param verificationId  The verification's UUID.
     * @param ttl             The duration for which the mapping is valid.
     */
    void put(String token, UUID organizationId, UUID verificationId, Duration ttl);

    /**
     * Retrieves the entry mapped to the given token, if it exists and is not expired.
     *
     * @param token  The token to look up.
     * @return       An Optional containing the entry if found, or empty otherwise.
     */
    Optional<Entry> get(String token);

    /**
     * Retrieves the token currently mapped to the given verification, if one exists.
     *
     * @param verificationId  The UUID of the verification operation.
     * @return                An Optional containing the token if it exists, or empty otherwise.
     */
    Optional<String> tokenFor(UUID verificationId);

    /**
     * Removes any token mapped to the given verification, effectively revoking access
     * for that verification operation.
     *
     * @param verificationId  The verification UUID whose token should be revoked.
     */
    void revokeByVerificationId(UUID verificationId);
}
