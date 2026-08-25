package com.kyc.repositories;

import com.kyc.entities.Verification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRepository extends JpaRepository<Verification, UUID> {

    Optional<Verification> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndExternalId(UUID organizationId, String externalId);
}
