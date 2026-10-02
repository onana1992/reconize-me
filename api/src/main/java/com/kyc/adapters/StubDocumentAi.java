package com.kyc.adapters;

import com.kyc.ports.DocumentAiPort;
import java.time.LocalDate;

public class StubDocumentAi implements DocumentAiPort {

    @Override
    public DocumentSignals analyze(byte[] documentImage, String sandboxScenario) {
        String scenario = sandboxScenario == null ? "approved" : sandboxScenario;
        return switch (scenario) {
            case "unsupported" -> new DocumentSignals(
                    "unknown", "ZZ", null, false, false, false, null, null, null, null, null, "stub");
            case "expired" -> new DocumentSignals(
                    "passport",
                    "FR",
                    null,
                    true,
                    true,
                    false,
                    "Marie",
                    "Dupont",
                    LocalDate.of(1990, 4, 12),
                    "XX0000000",
                    LocalDate.of(2020, 1, 1),
                    "stub");
            default -> new DocumentSignals(
                    "passport",
                    "FR",
                    null,
                    false,
                    true,
                    false,
                    "Marie",
                    "Dupont",
                    LocalDate.of(1990, 4, 12),
                    "XX0000000",
                    LocalDate.of(2030, 1, 1),
                    "stub");
        };
    }
}
