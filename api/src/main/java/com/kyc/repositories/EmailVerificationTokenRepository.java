package com.kyc.repositories;

import com.kyc.entities.EmailVerificationToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    List<EmailVerificationToken> findByUserIdAndConsumedAtIsNull(UUID userId);

    Optional<EmailVerificationToken> findFirstByUserIdOrderByExpiresAtDesc(UUID userId);
}
