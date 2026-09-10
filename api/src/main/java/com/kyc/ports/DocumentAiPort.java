package com.kyc.ports;

public interface DocumentAiPort {

    record DocumentSignals(String documentType, String documentCountry, boolean expired, boolean supported) {}

    DocumentSignals analyze(byte[] documentImage, String sandboxScenario);
}
