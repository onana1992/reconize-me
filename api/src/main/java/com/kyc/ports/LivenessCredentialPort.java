package com.kyc.ports;

import java.time.Instant;

/** Short-lived credentials the browser uses only for StartFaceLivenessSession. */
public interface LivenessCredentialPort {

    Issued issue();

    record Issued(String accessKeyId, String secretAccessKey, String sessionToken, Instant expiration) {}
}
