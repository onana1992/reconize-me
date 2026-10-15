package com.kyc.ports;

/** Rekognition Face Liveness. Create and read stay on the API; the browser streams the video. */
public interface FaceLivenessClient {

    String createSession(String clientRequestToken);

    LivenessResult getResults(String sessionId);

    /** {@code confidence} is in [0, 1]. {@code referenceImage} may be empty until the check succeeds. */
    record LivenessResult(double confidence, String status, byte[] referenceImage) {}
}
