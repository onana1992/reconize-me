package com.kyc.services;

import com.kyc.enums.SignalOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.ports.BiometricAiPort.BiometricSignals;
import com.kyc.ports.DocumentAiPort.DocumentSignals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IdvDecisionEngine {

    public static final String RULES_VERSION = "m4-1";
    public static final String LIVE_RULES_VERSION = "m5-1";
    public static final String VISION_RULES_VERSION = "vision-1";
    public static final double LOW_OCR_CONFIDENCE = 0.70;

    public static final double FACE_MATCH_FAIL = 0.75;
    public static final double FACE_MATCH_PASS = 0.90;

    public record Result(
            VerificationDecision decision,
            List<String> reasons,
            List<Signal> signals,
            Map<String, Object> extractedIdentity) {}

    public record Signal(String code, SignalOutcome outcome, Double score) {}

    public Result decide(String scenario) {
        String key = scenario == null || scenario.isBlank() ? "approved" : scenario.trim();
        return switch (key) {
            case "unsupported" -> declined("unsupported_document");
            case "expired" -> declined("document_expired");
            case "liveness_fail" -> declined("liveness_fail");
            case "mismatch" -> declined("face_match_fail");
            case "review" -> new Result(
                    VerificationDecision.REVIEW,
                    List.of("mrz_unavailable"),
                    List.of(
                            new Signal("mrz_unavailable", SignalOutcome.UNAVAILABLE, null),
                            new Signal("liveness_pass", SignalOutcome.PASS, 0.92),
                            new Signal("face_match_pass", SignalOutcome.PASS, 0.82)),
                    identity());
            default -> new Result(
                    VerificationDecision.APPROVED,
                    List.of("liveness_pass", "face_match_pass"),
                    List.of(
                            new Signal("authenticity_stub_pass", SignalOutcome.PASS, null),
                            new Signal("liveness_pass", SignalOutcome.PASS, 0.99),
                            new Signal("face_match_pass", SignalOutcome.PASS, 0.96)),
                    identity());
        };
    }

    public Result decideLive(DocumentSignals document, BiometricSignals biometric) {
        List<Signal> signals = new ArrayList<>();
        if ("textract_analyze_id".equals(document.provider())) {
            signals.add(new Signal("ocr_analyze_id", SignalOutcome.PASS, null));
        } else if ("textract_detect_text".equals(document.provider())) {
            signals.add(new Signal("ocr_detect_text", SignalOutcome.PASS, null));
        }
        signals.add(new Signal("mrz_unavailable", SignalOutcome.UNAVAILABLE, null));

        if (!document.supported()) {
            signals.add(new Signal("unsupported_document", SignalOutcome.FAIL, null));
            return new Result(
                    VerificationDecision.DECLINED, List.of("unsupported_document"), List.copyOf(signals), Map.of());
        }

        Map<String, Object> extracted = extracted(document);

        if (document.expired()) {
            signals.add(new Signal("document_expired", SignalOutcome.FAIL, null));
            return new Result(
                    VerificationDecision.DECLINED, List.of("document_expired"), List.copyOf(signals), extracted);
        }

        if (missingRequired(document)) {
            signals.add(new Signal("document_fields_incomplete", SignalOutcome.FAIL, null));
            return new Result(
                    VerificationDecision.REVIEW,
                    List.of("document_fields_incomplete"),
                    List.copyOf(signals),
                    extracted);
        }

        if (biometric == null || !biometric.livenessPass()) {
            signals.add(new Signal("liveness_fail", SignalOutcome.FAIL, biometric == null ? null : biometric.livenessScore()));
            return new Result(
                    VerificationDecision.DECLINED, List.of("liveness_fail"), List.copyOf(signals), extracted);
        }
        signals.add(new Signal("liveness_pass", SignalOutcome.PASS, biometric.livenessScore()));

        double score = biometric.faceMatchScore() == null ? 0.0 : biometric.faceMatchScore();
        if (score < FACE_MATCH_FAIL) {
            signals.add(new Signal("face_match_fail", SignalOutcome.FAIL, score));
            return new Result(
                    VerificationDecision.DECLINED, List.of("face_match_fail"), List.copyOf(signals), extracted);
        }
        if (score < FACE_MATCH_PASS) {
            signals.add(new Signal("face_match_borderline", SignalOutcome.FAIL, score));
            return new Result(
                    VerificationDecision.REVIEW, List.of("face_match_borderline"), List.copyOf(signals), extracted);
        }
        signals.add(new Signal("face_match_pass", SignalOutcome.PASS, score));
        return new Result(
                VerificationDecision.APPROVED,
                List.of("liveness_pass", "face_match_pass"),
                List.copyOf(signals),
                extracted);
    }

    public Result applyFace(Result document, com.kyc.ports.BiometricAiPort.BiometricSignals biometric) {
        Map<String, Object> identity = document.extractedIdentity() == null ? Map.of() : document.extractedIdentity();
        List<Signal> signals = new ArrayList<>();
        if (biometric == null || !biometric.livenessPass()) {
            signals.add(new Signal(
                    "liveness_fail", SignalOutcome.FAIL, biometric == null ? null : biometric.livenessScore()));
            return new Result(VerificationDecision.DECLINED, List.of("liveness_fail"), List.copyOf(signals), identity);
        }
        signals.add(new Signal("liveness_pass", SignalOutcome.PASS, biometric.livenessScore()));
        double score = biometric.faceMatchScore() == null ? 0.0 : biometric.faceMatchScore();
        if (score < FACE_MATCH_FAIL) {
            signals.add(new Signal("face_match_fail", SignalOutcome.FAIL, score));
            return new Result(VerificationDecision.DECLINED, List.of("face_match_fail"), List.copyOf(signals), identity);
        }
        if (score < FACE_MATCH_PASS) {
            signals.add(new Signal("face_match_borderline", SignalOutcome.FAIL, score));
            return new Result(VerificationDecision.REVIEW, List.of("face_match_borderline"), List.copyOf(signals), identity);
        }
        signals.add(new Signal("face_match_pass", SignalOutcome.PASS, score));
        return new Result(
                VerificationDecision.APPROVED,
                List.of("liveness_pass", "face_match_pass"),
                List.copyOf(signals),
                identity);
    }

    public Result providerUnavailable() {
        return review("provider_unavailable");
    }

    public Result review(String reason) {
        return new Result(
                VerificationDecision.REVIEW,
                List.of(reason),
                List.of(new Signal(reason, SignalOutcome.FAIL, null)),
                Map.of());
    }

    /**
     * Ordre du cahier §16. Cette chaîne ne pose pas {@code ocr_analyze_id}.
     * Une image illisible n'a pas de décision de fraude.
     */
    public VisionVerdict decideVision(DocumentSignals document, VisionFacts facts) {
        Map<String, Object> identity = facts.extractedIdentity() == null || facts.extractedIdentity().isEmpty()
                ? (document == null ? Map.of() : extracted(document))
                : facts.extractedIdentity();
        List<String> reasons = new ArrayList<>();
        String issue = null;
        if (!facts.readable()) {
            issue = "RECAPTURE";
            reasons.add(facts.captureReason() == null || facts.captureReason().isBlank()
                    ? "IMAGE_TOO_BLURRY"
                    : facts.captureReason());
        }
        if (facts.expired()) {
            issue = issue == null ? "REJECT" : issue;
            reasons.add("document_expired");
        }
        if (facts.unknown()) {
            issue = issue == null ? "REVIEW" : issue;
            reasons.add("DOCUMENT_UNKNOWN");
        }
        if (facts.missingRequired()) {
            issue = issue == null ? "REVIEW" : issue;
            reasons.add("MISSING_REQUIRED_FIELD");
        }
        if (facts.mrzFailed()) {
            issue = issue == null ? "REVIEW" : issue;
            reasons.add("MRZ_MISMATCH");
        }
        if (facts.lowOcr()) {
            issue = issue == null ? "REVIEW" : issue;
            reasons.add("LOW_OCR_CONFIDENCE");
        }
        if (facts.suspicious()) {
            issue = issue == null ? "REVIEW" : issue;
            reasons.add("SUSPICIOUS_INDICATOR");
        }
        if (issue == null && "HIGH_CONFIDENCE".equals(facts.band()) && !facts.hasError() && !facts.hasWarning()) {
            issue = "PASS";
        }
        if (issue == null) {
            issue = "REVIEW";
            reasons.add("LOW_CONFIDENCE");
        }
        VerificationDecision decision = switch (issue) {
            case "PASS" -> VerificationDecision.APPROVED;
            case "REJECT" -> VerificationDecision.DECLINED;
            case "RECAPTURE" -> null;
            default -> VerificationDecision.REVIEW;
        };
        List<Signal> signals = new ArrayList<>();
        for (String reason : reasons) {
            signals.add(new Signal(reason, SignalOutcome.FAIL, null));
        }
        return new VisionVerdict(
                issue,
                new Result(decision, List.copyOf(reasons), List.copyOf(signals), identity));
    }

    public record VisionFacts(
            boolean readable,
            String captureReason,
            boolean expired,
            boolean unknown,
            boolean missingRequired,
            boolean mrzFailed,
            boolean lowOcr,
            boolean suspicious,
            String band,
            boolean hasError,
            boolean hasWarning,
            Map<String, Object> extractedIdentity) {}

    public record VisionVerdict(String issue, Result result) {}

    private static boolean missingRequired(DocumentSignals document) {
        return blank(document.firstName())
                || blank(document.lastName())
                || document.birthDate() == null
                || blank(document.documentNumber())
                || document.expirationDate() == null;
    }

    private static Map<String, Object> extracted(DocumentSignals document) {
        Map<String, Object> out = new LinkedHashMap<>();
        put(out, "first_name", document.firstName());
        put(out, "last_name", document.lastName());
        put(out, "birth_date", date(document.birthDate()));
        put(out, "document_type", document.documentType());
        put(out, "document_country", document.documentCountry());
        put(out, "issuing_jurisdiction", document.issuingJurisdiction());
        put(out, "document_number", document.documentNumber());
        put(out, "expiration_date", date(document.expirationDate()));
        return out;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            map.put(key, value);
        }
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Result declined(String reason) {
        return new Result(
                VerificationDecision.DECLINED,
                List.of(reason),
                List.of(new Signal(reason, SignalOutcome.FAIL, null)),
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
