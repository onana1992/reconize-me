package com.kyc.adapters;

import com.kyc.ports.DocumentAiPort;

public class StubDocumentAi implements DocumentAiPort {

    @Override
    public DocumentSignals analyze(byte[] documentImage, String sandboxScenario) {
        String scenario = sandboxScenario == null ? "approved" : sandboxScenario;
        return switch (scenario) {
            case "unsupported" -> new DocumentSignals("unknown", "ZZ", false, false);
            case "expired" -> new DocumentSignals("passport", "FR", true, true);
            default -> new DocumentSignals("passport", "FR", false, true);
        };
    }
}
