package com.kyc.repositories;

import com.kyc.entities.AuthenticationSession;
import com.kyc.enums.AuthenticationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthenticationRepository extends JpaRepository<AuthenticationSession, UUID> {

    Optional<AuthenticationSession> findByIdAndOrganizationIdAndIntegrationId(
            UUID id, UUID organizationId, UUID integrationId);

    Optional<AuthenticationSession> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<AuthenticationSession> findByHostedTokenHash(String hostedTokenHash);

    @Query(
            """
            select a from AuthenticationSession a
            where a.organizationId = :org
              and a.integrationId = :integration
              and (:status is null or a.status = :status)
              and (:externalId is null or a.externalId = :externalId)
            order by a.createdAt desc, a.id desc
            """)
    List<AuthenticationSession> pageFirst(
            @Param("org") UUID organizationId,
            @Param("integration") UUID integrationId,
            @Param("status") AuthenticationStatus status,
            @Param("externalId") String externalId,
            Pageable pageable);

    @Query(
            """
            select a from AuthenticationSession a
            where a.organizationId = :org
              and a.integrationId = :integration
              and (:status is null or a.status = :status)
              and (:externalId is null or a.externalId = :externalId)
              and (a.createdAt < :createdAt or (a.createdAt = :createdAt and a.id < :id))
            order by a.createdAt desc, a.id desc
            """)
    List<AuthenticationSession> pageAfter(
            @Param("org") UUID organizationId,
            @Param("integration") UUID integrationId,
            @Param("status") AuthenticationStatus status,
            @Param("externalId") String externalId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable pageable);

    @Query(
            """
            select a from AuthenticationSession a
            where a.organizationId = :org
              and (:status is null or a.status = :status)
              and (:integrationId is null or a.integrationId = :integrationId)
            order by a.createdAt desc, a.id desc
            """)
    List<AuthenticationSession> pageOrgFirst(
            @Param("org") UUID organizationId,
            @Param("status") AuthenticationStatus status,
            @Param("integrationId") UUID integrationId,
            Pageable pageable);

    @Query(
            """
            select a from AuthenticationSession a
            where a.organizationId = :org
              and (:status is null or a.status = :status)
              and (:integrationId is null or a.integrationId = :integrationId)
              and (a.createdAt < :createdAt or (a.createdAt = :createdAt and a.id < :id))
            order by a.createdAt desc, a.id desc
            """)
    List<AuthenticationSession> pageOrgAfter(
            @Param("org") UUID organizationId,
            @Param("status") AuthenticationStatus status,
            @Param("integrationId") UUID integrationId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable pageable);

    @Query(
            """
            select i.mode, count(a.id)
            from AuthenticationSession a, Integration i
            where a.organizationId = :org
              and i.id = a.integrationId
            group by i.mode
            """)
    List<Object[]> countByIntegrationMode(@Param("org") UUID organizationId);
}
