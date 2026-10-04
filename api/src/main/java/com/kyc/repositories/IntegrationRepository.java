package com.kyc.repositories;

import com.kyc.entities.Integration;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    List<Integration> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    Optional<Integration> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Integration> findFirstByOrganizationIdAndProductAndModeOrderByCreatedAtAsc(
            UUID organizationId, ProductCode product, IntegrationMode mode);

    boolean existsByOrganizationIdAndProductAndNameIgnoreCase(UUID organizationId, ProductCode product, String name);
}
