package com.kyc.repositories;

import com.kyc.entities.IdempotencyKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKey.Pk> {

    Optional<IdempotencyKey> findByOrganizationIdAndKey(UUID organizationId, String key);
}
