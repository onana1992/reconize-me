package com.kyc.repositories;

import com.kyc.entities.Screening;
import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.ScreeningStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScreeningRepository extends JpaRepository<Screening, UUID> {

    Optional<Screening> findByIdAndOrganizationIdAndIntegrationId(UUID id, UUID organizationId, UUID integrationId);

    Optional<Screening> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            select s from Screening s
            where s.organizationId = :organizationId
              and (:integrationId is null or s.integrationId = :integrationId)
              and (:status is null or s.status = :status)
              and (:outcome is null or s.outcome = :outcome)
              and (:externalId is null or s.externalId = :externalId)
              and (s.createdAt < :createdAt or (s.createdAt = :createdAt and s.id < :id))
            order by s.createdAt desc, s.id desc
            """)
    List<Screening> page(
            UUID organizationId,
            UUID integrationId,
            ScreeningStatus status,
            ScreeningOutcome outcome,
            String externalId,
            Instant createdAt,
            UUID id,
            Pageable pageable);
}
