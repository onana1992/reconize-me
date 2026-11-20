package com.kyc.authenticity;

import java.util.ArrayList;
import java.util.List;

/**
 * Verdict global du moteur. L'alignement manqué est tranché avant les mesures.
 * Une pièce est suspecte quand au moins la moitié de ses zones le sont.
 * Une zone limite ne refuse pas la pièce. Une zone non mesurée redemande la photo.
 * Les codes retenus sont ceux des éléments qui ont fait le verdict.
 */
public final class AuthenticityVerdict {

    private AuthenticityVerdict() {}

    public static Decision alignmentFailed() {
        return new Decision("a_verifier", List.of("alignment_failed"));
    }

    public static Decision decide(List<Reading> readings) {
        int suspects = count(readings, "suspect");
        if (!readings.isEmpty() && suspects * 2 >= readings.size()) {
            return new Decision("suspect", codes(readings, "suspect"));
        }
        List<String> unmeasured = unmeasured(readings);
        if (!unmeasured.isEmpty()) {
            return new Decision("a_verifier", unmeasured);
        }
        return new Decision("conforme", List.of());
    }

    private static int count(List<Reading> readings, String status) {
        int count = 0;
        for (Reading reading : readings) {
            if (status.equals(reading.status())) {
                count++;
            }
        }
        return count;
    }

    private static List<String> unmeasured(List<Reading> readings) {
        List<String> codes = new ArrayList<>();
        for (Reading reading : readings) {
            if (("alignment_failed".equals(reading.code()) || "zone_unmeasured".equals(reading.code()))
                    && !codes.contains(reading.code())) {
                codes.add(reading.code());
            }
        }
        return List.copyOf(codes);
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
