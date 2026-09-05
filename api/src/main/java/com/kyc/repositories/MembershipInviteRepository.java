package com.kyc.repositories;

import com.kyc.entities.MembershipInvite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipInviteRepository extends JpaRepository<MembershipInvite, UUID> {

    Optional<MembershipInvite> findByTokenHash(String tokenHash);

    List<MembershipInvite> findByOrganizationIdAndAcceptedAtIsNullOrderByCreatedAtAsc(UUID organizationId);

    Optional<MembershipInvite> findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(String email);
}
