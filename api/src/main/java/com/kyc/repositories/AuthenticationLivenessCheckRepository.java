package com.kyc.repositories;

import com.kyc.entities.AuthenticationLivenessCheck;
import com.kyc.enums.LivenessCheckStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationLivenessCheckRepository extends JpaRepository<AuthenticationLivenessCheck, UUID> {

    long countByAuthenticationId(UUID authenticationId);

    long countByAuthenticationIdAndStatus(UUID authenticationId, LivenessCheckStatus status);

    Optional<AuthenticationLivenessCheck> findFirstByAuthenticationIdAndStatusOrderByAttemptDesc(
            UUID authenticationId, LivenessCheckStatus status);
}
