package com.kyc.ports;

/** Abstraction over Textract AnalyzeID so tests can inject a fixture without AWS. */
@FunctionalInterface
public interface AnalyzeIdClient {

    /** Raw AnalyzeID-shaped JSON (IdentityDocuments / IdentityDocumentFields). */
    String analyzeId(byte[] documentImage);
}
