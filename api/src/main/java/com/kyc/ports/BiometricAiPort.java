package com.kyc.ports;

public interface BiometricAiPort {

    record BiometricSignals(boolean livenessPass, boolean faceMatchPass, Double livenessScore, Double faceMatchScore) {}

    BiometricSignals evaluate(byte[] documentImage, byte[] selfieImage, String sandboxScenario);
}
