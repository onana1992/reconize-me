package com.kyc.ports;

import java.time.LocalDate;

public interface DocumentAiPort {

    record DocumentSignals(
            String documentType,
            String documentCountry,
            String issuingJurisdiction,
            boolean expired,
            boolean supported,
            boolean mrzAvailable,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String documentNumber,
            LocalDate expirationDate,
            String provider) {}

    DocumentSignals analyze(byte[] documentImage, String sandboxScenario);
}
