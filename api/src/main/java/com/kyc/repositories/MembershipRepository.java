package com.kyc.repositories;

import com.kyc.entities.Membership;
import com.kyc.entities.MembershipId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {

    Optional<Membership> findByUserId(UUID userId);

    List<Membership> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    boolean existsByUserId(UUID userId);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    long countByOrganizationIdAndRole(UUID organizationId, String role);

    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, String role, String status);
}
