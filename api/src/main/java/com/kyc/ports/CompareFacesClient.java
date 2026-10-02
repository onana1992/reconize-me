package com.kyc.ports;

/** Abstraction over Rekognition CompareFaces for tests without AWS. */
@FunctionalInterface
public interface CompareFacesClient {

    /** Similarity in [0, 1]. */
    double compare(byte[] documentImage, byte[] selfieImage);
}
