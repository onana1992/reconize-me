package com.kyc.repositories;

import com.kyc.entities.VerificationAuthenticityAssessment;
import com.kyc.entities.VerificationAuthenticityAssessmentKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationAuthenticityAssessmentRepository
        extends JpaRepository<VerificationAuthenticityAssessment, VerificationAuthenticityAssessmentKey> {

    Optional<VerificationAuthenticityAssessment> findByVerificationIdAndSide(UUID verificationId, String side);

    List<VerificationAuthenticityAssessment> findByVerificationIdOrderBySideAsc(UUID verificationId);
}
