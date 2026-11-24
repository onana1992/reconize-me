package com.kyc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.entities.Enrollment;
import com.kyc.entities.Organization;
import com.kyc.enums.EnrollmentSource;
import com.kyc.enums.EnrollmentStatus;
import com.kyc.repositories.EnrollmentRepository;
import com.kyc.repositories.OrganizationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EnrollmentPersistenceTest {

    @Autowired
    private EnrollmentRepository enrollments;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private EntityManager entityManager;

    @Test
    void twoActiveEnrollmentsForSameCoupleAreRejected() {
        Instant now = Instant.parse("2026-11-22T12:00:00Z");
        UUID orgId = UUID.randomUUID();
        organizations.save(new Organization(orgId, "Auth Org", "auth-org-" + orgId, now));

        enrollments.saveAndFlush(new Enrollment(
                UUID.randomUUID(),
                orgId,
                "user-1",
                EnrollmentSource.IDV,
                UUID.randomUUID(),
                "face-a",
                "collection-a",
                now));

        Enrollment duplicate = new Enrollment(
                UUID.randomUUID(),
                orgId,
                "user-1",
                EnrollmentSource.IDV,
                UUID.randomUUID(),
                "face-b",
                "collection-a",
                now.plusSeconds(1));
        assertThrows(DataIntegrityViolationException.class, () -> enrollments.saveAndFlush(duplicate));
        entityManager.clear();

        Enrollment active = enrollments
                .findByOrganizationIdAndExternalIdAndStatus(orgId, "user-1", EnrollmentStatus.ACTIVE)
                .orElseThrow();
        active.retire(now.plusSeconds(2));
        enrollments.saveAndFlush(active);

        enrollments.saveAndFlush(new Enrollment(
                UUID.randomUUID(),
                orgId,
                "user-1",
                EnrollmentSource.IDV,
                UUID.randomUUID(),
                "face-c",
                "collection-a",
                now.plusSeconds(3)));
        assertTrue(enrollments
                .findByOrganizationIdAndExternalIdAndStatus(orgId, "user-1", EnrollmentStatus.ACTIVE)
                .isPresent());
    }
}
