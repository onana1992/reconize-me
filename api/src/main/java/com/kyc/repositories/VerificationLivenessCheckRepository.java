package com.kyc.repositories;

import com.kyc.entities.VerificationLivenessCheck;
import com.kyc.enums.LivenessCheckStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationLivenessCheckRepository extends JpaRepository<VerificationLivenessCheck, UUID> {

    long countByVerificationId(UUID verificationId);

    long countByVerificationIdAndBilledTrue(UUID verificationId);

    Optional<VerificationLivenessCheck> findFirstByVerificationIdAndStatusOrderByAttemptDesc(
            UUID verificationId, LivenessCheckStatus status);
}
