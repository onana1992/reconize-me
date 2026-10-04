package com.kyc.repositories;

import com.kyc.entities.VerificationMedia;
import com.kyc.enums.MediaKind;
import com.kyc.enums.MediaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationMediaRepository extends JpaRepository<VerificationMedia, UUID> {

    Optional<VerificationMedia> findByVerificationIdAndKindAndAttempt(UUID verificationId, MediaKind kind, int attempt);

    List<VerificationMedia> findByVerificationIdOrderByCreatedAtAsc(UUID verificationId);

    long countByVerificationIdAndKind(UUID verificationId, MediaKind kind);

    long countByVerificationIdAndKindAndStatus(UUID verificationId, MediaKind kind, MediaStatus status);

    Optional<VerificationMedia> findFirstByVerificationIdAndKindAndStatusOrderByAttemptDesc(
            UUID verificationId, MediaKind kind, MediaStatus status);
}
