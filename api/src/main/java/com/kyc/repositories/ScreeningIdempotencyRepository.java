package com.kyc.repositories;

import com.kyc.entities.ScreeningIdempotencyKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningIdempotencyRepository
        extends JpaRepository<ScreeningIdempotencyKey, ScreeningIdempotencyKey.Pk> {

    Optional<ScreeningIdempotencyKey> findByOrganizationIdAndKey(UUID organizationId, String key);
}
