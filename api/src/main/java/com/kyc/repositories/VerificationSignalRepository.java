package com.kyc.repositories;

import com.kyc.entities.VerificationSignal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationSignalRepository extends JpaRepository<VerificationSignal, UUID> {

    List<VerificationSignal> findByVerificationIdOrderByCreatedAtAsc(UUID verificationId);
}
