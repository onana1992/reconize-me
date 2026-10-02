package com.kyc.repositories;

import com.kyc.entities.WebhookEndpoint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    Optional<WebhookEndpoint> findByIntegrationId(UUID integrationId);

    Optional<WebhookEndpoint> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<WebhookEndpoint> findByIntegrationIdAndOrganizationId(UUID integrationId, UUID organizationId);
}
