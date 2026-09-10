package com.kyc.services;

import java.util.List;
import java.util.Map;

public class IdvDecisionEngine {

    public static final String RULES_VERSION = "m4-1";

    public record Result(
            String decision, List<String> reasons, List<Signal> signals, Map<String, Object> extractedIdentity) {}

    public record Signal(String code, String outcome, Double score) {}

    public Result decide(String scenario) {
        String key = scenario == null || scenario.isBlank() ? "approved" : scenario.trim();
        return switch (key) {
            case "unsupported" -> declined("unsupported_document");
            case "expired" -> declined("document_expired");
            case "liveness_fail" -> declined("liveness_fail");
            case "mismatch" -> declined("face_match_fail");
            case "review" -> new Result(
                    "review",
                    List.of("mrz_unavailable"),
                    List.of(
                            new Signal("mrz_unavailable", "unavailable", null),
                            new Signal("liveness_pass", "pass", 0.92),
                            new Signal("face_match_pass", "pass", 0.82)),
                    identity());
            default -> new Result(
                    "approved",
                    List.of("liveness_pass", "face_match_pass"),
                    List.of(
                            new Signal("authenticity_stub_pass", "pass", null),
                            new Signal("liveness_pass", "pass", 0.99),
                            new Signal("face_match_pass", "pass", 0.96)),
                    identity());
        };
    }

    private static Result declined(String reason) {
        return new Result(
                "declined",
                List.of(reason),
                List.of(new Signal(reason, "fail", null)),
                Map.of());
    }

    private static Map<String, Object> identity() {
        return Map.of(
                "first_name",
                "Marie",
                "last_name",
                "Dupont",
                "birth_date",
                "1990-04-12",
                "document_type",
                "passport",
                "document_country",
                "FR",
                "document_number",
                "XX0000000");
    }
}
