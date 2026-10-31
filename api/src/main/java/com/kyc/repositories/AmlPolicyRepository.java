package com.kyc.repositories;

import com.kyc.entities.AmlPolicyEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlPolicyRepository extends JpaRepository<AmlPolicyEntity, UUID> {

    Optional<AmlPolicyEntity> findByIntegrationIdAndActiveTrue(UUID integrationId);

    List<AmlPolicyEntity> findByIntegrationIdOrderByVersionDesc(UUID integrationId);

    Optional<AmlPolicyEntity> findTopByIntegrationIdOrderByVersionDesc(UUID integrationId);

    List<AmlPolicyEntity> findByOrganizationIdAndActiveTrueAndAttachToIdentityTrueOrderByPublishedAtDesc(
            UUID organizationId);
}
