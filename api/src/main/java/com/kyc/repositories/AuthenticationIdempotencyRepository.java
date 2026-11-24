package com.kyc.repositories;

import com.kyc.entities.AuthenticationIdempotencyKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationIdempotencyRepository
        extends JpaRepository<AuthenticationIdempotencyKey, AuthenticationIdempotencyKey.Pk> {

    Optional<AuthenticationIdempotencyKey> findByOrganizationIdAndKey(UUID organizationId, String key);
}
