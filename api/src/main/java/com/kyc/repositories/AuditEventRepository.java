package com.kyc.repositories;

import com.kyc.entities.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByResourceIdOrderByIdAsc(UUID resourceId);

    List<AuditEvent> findByOrganizationIdOrderByIdDesc(UUID organizationId, Pageable pageable);

    List<AuditEvent> findByOrganizationIdAndIdLessThanOrderByIdDesc(
            UUID organizationId, long id, Pageable pageable);

    List<AuditEvent> findByOrganizationIdAndActionOrderByIdDesc(
            UUID organizationId, String action, Pageable pageable);

    List<AuditEvent> findByOrganizationIdAndActionAndIdLessThanOrderByIdDesc(
            UUID organizationId, String action, long id, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AuditEvent e where e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
