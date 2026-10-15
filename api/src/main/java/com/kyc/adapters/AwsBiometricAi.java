package com.kyc.adapters;

import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.FaceLivenessClient;
import com.kyc.ports.FaceLivenessClient.LivenessResult;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.services.IdvDecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CompareFaces runs only after a succeeded Face Liveness check at or above {@link IdvDecisionEngine#LIVENESS_PASS}.
 * {@code livenessPass} on the signals means the Rekognition status was SUCCEEDED. The engine applies the threshold.
 */
public class AwsBiometricAi implements BiometricAiPort {

    private static final Logger log = LoggerFactory.getLogger(AwsBiometricAi.class);

    private final CompareFacesClient faces;
    private final FaceLivenessClient liveness;

    public AwsBiometricAi(CompareFacesClient faces, FaceLivenessClient liveness) {
        this.faces = faces;
        this.liveness = liveness;
    }

    /**
     * No liveness result on this path. Callers that have a session use {@link #match}.
     */
    @Override
    public BiometricSignals evaluate(byte[] documentImage, byte[] selfieImage, String sandboxScenario) {
        return new BiometricSignals(false, false, null, null);
    }

    /** Reads the liveness result, then CompareFaces on the reference image only. */
    public BiometricSignals assess(String sessionId, byte[] documentImage, byte[] selfieImage) {
        try {
            return match(documentImage, liveness.getResults(sessionId));
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Face Liveness results failed");
            throw new ProviderUnavailableException("Rekognition Face Liveness unavailable", e);
        }
    }

    /**
     * CompareFaces source is {@code check.referenceImage()}. A separate selfie is never sent.
     * Below the liveness gate, CompareFaces is not called.
     */
    public BiometricSignals match(byte[] documentImage, LivenessResult check) {
        if (check == null) {
            return new BiometricSignals(false, false, null, null);
        }
        boolean statusOk = "SUCCEEDED".equalsIgnoreCase(check.status());
        double confidence = Math.max(0.0, Math.min(1.0, check.confidence()));
        byte[] reference = check.referenceImage();
        if (!statusOk || confidence < IdvDecisionEngine.LIVENESS_PASS || reference == null || reference.length == 0) {
            log.info("Face Liveness below gate statusOk={} confidence={}", statusOk, confidence);
            return new BiometricSignals(statusOk, false, confidence, null);
        }
        long start = System.nanoTime();
        try {
            double score = faces.compare(documentImage, reference);
            double clamped = Math.max(0.0, Math.min(1.0, score));
            log.info("CompareFaces completed durationMs={}", (System.nanoTime() - start) / 1_000_000L);
            return new BiometricSignals(true, clamped >= IdvDecisionEngine.FACE_MATCH_PASS, confidence, clamped);
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("CompareFaces failed durationMs={}", (System.nanoTime() - start) / 1_000_000L);
            throw new ProviderUnavailableException("Rekognition CompareFaces unavailable", e);
        }
    }
}
