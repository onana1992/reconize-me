package com.kyc.repositories;

import com.kyc.entities.Verification;
import com.kyc.entities.VerificationStatus;
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

    @Query(
            """
            SELECT v FROM Verification v
            WHERE v.organizationId = :organizationId
              AND (:status IS NULL OR v.status = :status)
              AND (:externalId IS NULL OR v.externalId = :externalId)
              AND (
                :hasCursor = false
                OR v.createdAt < :cursorCreatedAt
                OR (v.createdAt = :cursorCreatedAt AND v.id < :cursorId)
              )
            ORDER BY v.createdAt DESC, v.id DESC
            """)
    List<Verification> search(
            @Param("organizationId") UUID organizationId,
            @Param("status") VerificationStatus status,
            @Param("externalId") String externalId,
            @Param("hasCursor") boolean hasCursor,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
