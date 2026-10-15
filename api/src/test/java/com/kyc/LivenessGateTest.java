package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.kyc.adapters.AwsBiometricAi;
import com.kyc.enums.VerificationDecision;
import com.kyc.ports.BiometricAiPort.BiometricSignals;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.FaceLivenessClient;
import com.kyc.services.IdvDecisionEngine;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LivenessGateTest {

    private final IdvDecisionEngine engine = new IdvDecisionEngine();

    @Test
    void lowConfidenceSkipsCompareAndDeclines() {
        AtomicInteger compares = new AtomicInteger();
        byte[] selfie = new byte[] {1, 2, 3};
        AwsBiometricAi ai = ai(compares, 0.96, new FaceLivenessClient.LivenessResult(0.50, "SUCCEEDED", new byte[] {9}));

        BiometricSignals signals = ai.assess("session-1", new byte[] {4}, selfie);

        assertEquals(0, compares.get());
        IdvDecisionEngine.Result decision = engine.applyFace(approvedDocument(), signals);
        assertEquals(VerificationDecision.DECLINED, decision.decision());
        assertEquals("liveness_fail", decision.reasons().get(0));
        assertEquals(0.50, decision.signals().get(0).score());
    }

    @Test
    void highConfidenceComparesTheReferenceImage() {
        AtomicInteger compares = new AtomicInteger();
        byte[] selfie = new byte[] {7, 8};
        byte[] reference = new byte[] {9, 9, 9};
        byte[][] seen = new byte[1][];
        AwsBiometricAi ai = new AwsBiometricAi(
                (document, face) -> {
                    compares.incrementAndGet();
                    seen[0] = face;
                    return 0.96;
                },
                new FaceLivenessClient() {
                    @Override
                    public String createSession(String clientRequestToken) {
                        throw new AssertionError("createSession");
                    }

                    @Override
                    public LivenessResult getResults(String sessionId) {
                        return new LivenessResult(0.90, "SUCCEEDED", reference);
                    }
                });

        BiometricSignals signals = ai.assess("session-2", new byte[] {4}, selfie);

        assertEquals(1, compares.get());
        assertSame(reference, seen[0]);
        IdvDecisionEngine.Result decision = engine.applyFace(approvedDocument(), signals);
        assertEquals(VerificationDecision.APPROVED, decision.decision());
        assertEquals("liveness_pass", decision.reasons().get(0));
    }

    @Test
    void engineRejectsAForcedPassBelowTheThreshold() {
        BiometricSignals forced = new BiometricSignals(true, true, 0.50, 0.96);
        IdvDecisionEngine.Result decision = engine.applyFace(approvedDocument(), forced);
        assertEquals(VerificationDecision.DECLINED, decision.decision());
        assertEquals("liveness_fail", decision.reasons().get(0));
    }

    @Test
    void failedStatusDoesNotCompare() {
        AtomicInteger compares = new AtomicInteger();
        AwsBiometricAi ai = ai(compares, 0.96, new FaceLivenessClient.LivenessResult(0.95, "FAILED", new byte[] {1}));

        BiometricSignals signals = ai.assess("session-3", new byte[] {4}, new byte[] {5});

        assertEquals(0, compares.get());
        assertEquals(VerificationDecision.DECLINED, engine.applyFace(approvedDocument(), signals).decision());
    }

    @Test
    void boundaryConfidenceCallsCompare() {
        AtomicInteger compares = new AtomicInteger();
        AwsBiometricAi ai = ai(compares, 0.80, new FaceLivenessClient.LivenessResult(0.80, "SUCCEEDED", new byte[] {1}));

        BiometricSignals signals = ai.assess("session-4", new byte[] {4}, new byte[] {5});

        assertEquals(1, compares.get());
        assertEquals(VerificationDecision.REVIEW, engine.applyFace(approvedDocument(), signals).decision());
        assertEquals("face_match_borderline", engine.applyFace(approvedDocument(), signals).reasons().get(0));
    }

    private static AwsBiometricAi ai(AtomicInteger compares, double similarity, FaceLivenessClient.LivenessResult result) {
        CompareFacesClient faces = (document, selfie) -> {
            compares.incrementAndGet();
            return similarity;
        };
        FaceLivenessClient liveness = new FaceLivenessClient() {
            @Override
            public String createSession(String clientRequestToken) {
                throw new AssertionError("createSession");
            }

            @Override
            public LivenessResult getResults(String sessionId) {
                return result;
            }
        };
        return new AwsBiometricAi(faces, liveness);
    }

    private static IdvDecisionEngine.Result approvedDocument() {
        return new IdvDecisionEngine.Result(
                VerificationDecision.APPROVED, java.util.List.of(), java.util.List.of(), Map.of("document_type", "driving_license"));
    }
}
