package com.kyc.services;

import com.kyc.entities.Verification;
import com.kyc.repositories.VerificationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {

    private final VerificationRepository verificationRepository;

    public VerificationService(VerificationRepository verificationRepository) {
        this.verificationRepository = verificationRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Verification> findForOrganization(UUID id, UUID organizationId) {
        return verificationRepository.findByIdAndOrganizationId(id, organizationId);
    }
}
