package com.kyc.repositories;

import com.kyc.entities.IdvPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdvPolicyRepository extends JpaRepository<IdvPolicy, UUID> {

    Optional<IdvPolicy> findByOrganizationIdAndActiveTrue(UUID organizationId);

    List<IdvPolicy> findByOrganizationIdOrderByVersionDesc(UUID organizationId);
}
