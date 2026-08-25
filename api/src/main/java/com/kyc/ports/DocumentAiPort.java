package com.kyc.ports;

public interface DocumentAiPort {

    String analyzeIdentityDocument(byte[] image);
}
