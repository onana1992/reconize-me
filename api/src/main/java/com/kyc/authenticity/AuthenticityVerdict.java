package com.kyc.authenticity;

import java.util.ArrayList;
import java.util.List;

/**
 * Verdict global du moteur. L'alignement manqué est tranché avant les mesures.
 * Sinon la première ligne qui s'applique gagne : un {@code suspect}, puis un {@code a_verifier}, sinon {@code conforme}.
 * Les codes retenus sont ceux des éléments qui ont fait le verdict.
 */
public final class AuthenticityVerdict {

    private AuthenticityVerdict() {}

    public static Decision alignmentFailed() {
        return new Decision("a_verifier", List.of("alignment_failed"));
    }

    public static Decision decide(List<Reading> readings) {
        if (any(readings, "suspect")) {
            return new Decision("suspect", codes(readings, "suspect"));
        }
        if (any(readings, "a_verifier")) {
            return new Decision("a_verifier", codes(readings, "a_verifier"));
        }
        return new Decision("conforme", List.of());
    }

    private static boolean any(List<Reading> readings, String status) {
        for (Reading reading : readings) {
            if (status.equals(reading.status())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> codes(List<Reading> readings, String status) {
        List<String> codes = new ArrayList<>();
        for (Reading reading : readings) {
            if (status.equals(reading.status()) && reading.code() != null && !codes.contains(reading.code())) {
                codes.add(reading.code());
            }
        }
        return List.copyOf(codes);
    }

    public record Reading(String status, String code) {}

    public record Decision(String verdict, List<String> codes) {}
}
