package com.kyc.repositories;

import com.kyc.entities.AmlPolicyEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlPolicyRepository extends JpaRepository<AmlPolicyEntity, UUID> {

    Optional<AmlPolicyEntity> findByOrganizationIdAndActiveTrue(UUID organizationId);

    List<AmlPolicyEntity> findByOrganizationIdOrderByVersionDesc(UUID organizationId);

    Optional<AmlPolicyEntity> findTopByOrganizationIdOrderByVersionDesc(UUID organizationId);
}
