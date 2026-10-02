package com.kyc.repositories;

import com.kyc.entities.WebhookDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<WebhookDelivery> findByIdAndIntegrationId(UUID id, UUID integrationId);

    List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId, Pageable pageable);

    @Query(
            """
            select d from WebhookDelivery d
            where d.status = 'pending' and d.nextAttemptAt <= :now
            order by d.nextAttemptAt asc
            """)
    List<WebhookDelivery> findDue(@Param("now") Instant now, Pageable pageable);

    boolean existsByVerificationIdAndEventTypeAndDecisionFingerprint(
            UUID verificationId, String eventType, String decisionFingerprint);
}
