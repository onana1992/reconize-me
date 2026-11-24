package com.kyc.repositories;

import com.kyc.entities.AuthenticationPolicyEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationPolicyRepository extends JpaRepository<AuthenticationPolicyEntity, UUID> {

    Optional<AuthenticationPolicyEntity> findByIntegrationIdAndActiveTrue(UUID integrationId);

    Optional<AuthenticationPolicyEntity> findByIntegrationIdAndVersion(UUID integrationId, int version);

    List<AuthenticationPolicyEntity> findByIntegrationIdOrderByVersionDesc(UUID integrationId);
}
