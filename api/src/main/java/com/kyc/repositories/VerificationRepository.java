package com.kyc.repositories;

import com.kyc.entities.Verification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationRepository extends JpaRepository<Verification, UUID> {

    Optional<Verification> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Verification> findByHostedTokenHash(String hostedTokenHash);

    boolean existsByOrganizationIdAndExternalId(UUID organizationId, String externalId);

    List<Verification> findByOrganizationIdOrderByCreatedAtDescIdDesc(UUID organizationId, Pageable pageable);

    @Query(
            """
            select v from Verification v
            where v.organizationId = :org
              and (:status is null or v.status = :status)
              and (:integrationId is null or v.integrationId = :integrationId)
              and (v.createdAt < :created or (v.createdAt = :created and v.id < :id))
            order by v.createdAt desc, v.id desc
            """)
    List<Verification> pageAfter(
            @Param("org") UUID organizationId,
            @Param("status") String status,
            @Param("integrationId") UUID integrationId,
            @Param("created") Instant created,
            @Param("id") UUID id,
            Pageable pageable);

    @Query(
            """
            select v from Verification v
            where v.organizationId = :org
              and (:status is null or v.status = :status)
              and (:integrationId is null or v.integrationId = :integrationId)
            order by v.createdAt desc, v.id desc
            """)
    List<Verification> pageFirst(
            @Param("org") UUID organizationId,
            @Param("status") String status,
            @Param("integrationId") UUID integrationId,
            Pageable pageable);

    @Query(
            """
            select i.mode, count(v.id)
            from Verification v, Integration i
            where v.organizationId = :org
              and i.id = v.integrationId
            group by i.mode
            """)
    List<Object[]> countByIntegrationMode(@Param("org") UUID organizationId);
}
