package com.kyc.adapters;

import com.kyc.ports.BiometricAiPort;

public class StubBiometricAi implements BiometricAiPort {

    @Override
    public BiometricSignals evaluate(byte[] documentImage, byte[] selfieImage, String sandboxScenario) {
        String scenario = sandboxScenario == null ? "approved" : sandboxScenario;
        return switch (scenario) {
            case "liveness_fail" -> new BiometricSignals(false, false, 0.12, null);
            case "mismatch" -> new BiometricSignals(true, false, 0.91, 0.31);
            case "review" -> new BiometricSignals(true, true, 0.92, 0.82);
            default -> new BiometricSignals(true, true, 0.99, 0.96);
        };
    }
}
