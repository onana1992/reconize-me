package com.kyc.repositories;

import com.kyc.entities.VerificationMedia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationMediaRepository extends JpaRepository<VerificationMedia, UUID> {

    Optional<VerificationMedia> findByVerificationIdAndKindAndAttempt(UUID verificationId, String kind, int attempt);

    List<VerificationMedia> findByVerificationIdOrderByCreatedAtAsc(UUID verificationId);

    long countByVerificationIdAndKind(UUID verificationId, String kind);

    long countByVerificationIdAndKindAndStatus(UUID verificationId, String kind, String status);

    Optional<VerificationMedia> findFirstByVerificationIdAndKindAndStatusOrderByAttemptDesc(
            UUID verificationId, String kind, String status);
}
