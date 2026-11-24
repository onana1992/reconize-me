package com.kyc.repositories;

import com.kyc.entities.Enrollment;
import com.kyc.enums.EnrollmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByOrganizationIdAndExternalIdAndStatus(
            UUID organizationId, String externalId, EnrollmentStatus status);

    Optional<Enrollment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Enrollment> findByOrganizationIdAndExternalIdOrderByCreatedAtDesc(
            UUID organizationId, String externalId);

    List<Enrollment> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}
