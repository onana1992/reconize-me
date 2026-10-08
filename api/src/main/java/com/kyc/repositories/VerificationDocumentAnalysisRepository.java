package com.kyc.repositories;

import com.kyc.entities.VerificationDocumentAnalysis;
import com.kyc.entities.VerificationDocumentAnalysisKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationDocumentAnalysisRepository
        extends JpaRepository<VerificationDocumentAnalysis, VerificationDocumentAnalysisKey> {

    Optional<VerificationDocumentAnalysis> findByVerificationIdAndSide(UUID verificationId, String side);
}
