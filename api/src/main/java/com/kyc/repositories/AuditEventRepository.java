package com.kyc.repositories;

import com.kyc.entities.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByResourceIdOrderByIdAsc(UUID resourceId);
}
