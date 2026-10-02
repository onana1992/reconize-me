package com.kyc.adapters;

import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live biometrics: CompareFaces for face match; Face Liveness AWS is out of M5 — liveness = stub pass when
 * selfie quality already accepted (caller only invokes this after accepted selfie).
 */
public class AwsBiometricAi implements BiometricAiPort {

    private static final Logger log = LoggerFactory.getLogger(AwsBiometricAi.class);

    private final CompareFacesClient client;

    public AwsBiometricAi(CompareFacesClient client) {
        this.client = client;
    }

    @Override
    public BiometricSignals evaluate(byte[] documentImage, byte[] selfieImage, String sandboxScenario) {
        long start = System.nanoTime();
        try {
            double score = client.compare(documentImage, selfieImage);
            double clamped = Math.max(0.0, Math.min(1.0, score));
            log.info("CompareFaces completed durationMs={}", (System.nanoTime() - start) / 1_000_000L);
            // Liveness stub: media already accepted → pass (CDC §17.9 amended for M5).
            return new BiometricSignals(true, clamped >= 0.90, 1.0, clamped);
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("CompareFaces failed durationMs={}", (System.nanoTime() - start) / 1_000_000L);
            throw new ProviderUnavailableException("Rekognition CompareFaces unavailable", e);
        }
    }
}
