package com.kyc.repositories;

import com.kyc.entities.ApiKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByOrganizationIdAndRevokedFalse(UUID organizationId);

    List<ApiKey> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<ApiKey> findByKeyPrefixAndRevokedFalse(String keyPrefix);

    List<ApiKey> findByIntegrationIdOrderByCreatedAtDesc(UUID integrationId);

    boolean existsByIntegrationId(UUID integrationId);
}
