package com.kyc.repositories;

import com.kyc.entities.ScreeningHit;
import com.kyc.enums.HitDisposition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScreeningHitRepository extends JpaRepository<ScreeningHit, UUID> {

    List<ScreeningHit> findByScreeningIdOrderByScoreDescEntityIdAsc(UUID screeningId);

    Optional<ScreeningHit> findByIdAndScreeningId(UUID id, UUID screeningId);

    @Query("""
            select h from ScreeningHit h, Screening s
            where h.screeningId = s.id
              and s.organizationId = :organizationId
              and s.normalizedSubject = :normalizedSubject
              and s.status = com.kyc.enums.ScreeningStatus.COMPLETED
              and h.entityId = :entityId
              and h.disposition <> :unreviewed
              and s.id <> :currentId
            order by s.createdAt desc
            """)
    List<ScreeningHit> prior(
            UUID organizationId,
            String normalizedSubject,
            String entityId,
            UUID currentId,
            HitDisposition unreviewed);
}
