package com.kyc.repositories;

import com.kyc.entities.Consent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    boolean existsByVerificationId(UUID verificationId);

    Optional<Consent> findByVerificationId(UUID verificationId);
}
