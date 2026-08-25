package com.kyc.ports;

public interface BiometricAiPort {

    double compareFaces(byte[] documentPortrait, byte[] selfie);
}
