package com.kyc.dto;

import com.kyc.entities.Verification;
import java.util.Locale;
import java.util.Map;

public final class VerificationResponses {

    private VerificationResponses() {}

    public static VerificationResponse from(
            Verification verification, String hostedUrl, Map<String, Object> metadata) {
        ApplicantDto applicant = applicantOf(verification);
        return new VerificationResponse(
                verification.getId(),
                verification.getExternalId(),
                verification.getStatus().name().toLowerCase(Locale.ROOT),
                applicant,
                hostedUrl,
                verification.getHostedExpiresAt(),
                metadata == null ? Map.of() : metadata,
                verification.getCreatedAt(),
                verification.getUpdatedAt());
    }

    private static ApplicantDto applicantOf(Verification verification) {
        if (verification.getApplicantFirstName() == null
                && verification.getApplicantLastName() == null
                && verification.getApplicantEmail() == null) {
            return null;
        }
        return new ApplicantDto(
                verification.getApplicantFirstName(),
                verification.getApplicantLastName(),
                verification.getApplicantEmail());
    }
}
