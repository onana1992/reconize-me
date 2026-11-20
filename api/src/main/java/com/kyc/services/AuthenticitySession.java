package com.kyc.services;

import com.kyc.authenticity.PhotoImage;
import com.kyc.authenticity.TemplateRegistry;
import com.kyc.config.KycProperties;
import com.kyc.dto.authenticity.AuthenticityCheckResponse;
import com.kyc.dto.authenticity.FixedBlock;
import com.kyc.dto.authenticity.PositionBlock;
import com.kyc.dto.documentia.ParsedDocument.Classification;
import com.kyc.entities.Verification;
import com.kyc.enums.SignalOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.services.IdvDecisionEngine.Result;
import com.kyc.services.IdvDecisionEngine.Signal;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Branche template-1 sur la session live, après la classe Document IA.
 * Le flag éteint laisse le parcours inchangé.
 */
@Service
public class AuthenticitySession {

    private static final Logger log = LoggerFactory.getLogger(AuthenticitySession.class);

    private final boolean enabled;
    private final TemplateRegistry registry;
    private final AuthenticityLabService lab;

    @Autowired
    public AuthenticitySession(KycProperties properties, TemplateRegistry registry, AuthenticityLabService lab) {
        this(properties.authenticity().labEnabled(), registry, lab);
    }

    AuthenticitySession(boolean enabled, TemplateRegistry registry, AuthenticityLabService lab) {
        this.enabled = enabled;
        this.registry = registry;
        this.lab = lab;
    }

    public Outcome assess(Classification classification, String side, byte[] image) {
        return assess(classification, side, image, true);
    }

    /**
     * @param required un gabarit absent bloque l'approbation. Sinon la face est ignorée.
     */
    public Outcome assess(Classification classification, String side, byte[] image, boolean required) {
        if (!enabled) {
            return Outcome.skipped();
        }
        String documentId = documentId(classification, side);
        if (documentId == null || registry.find(documentId).isEmpty()) {
            return required ? Outcome.unsupported(documentId) : Outcome.skipped();
        }
        BufferedImage decoded = PhotoImage.decode(image);
        if (decoded == null) {
            return Outcome.recapture(documentId, "alignment_failed");
        }
        AuthenticityCheckResponse response = lab.decideImage(documentId, decoded);
        return Outcome.from(response);
    }

    /**
     * Une fiche ancienne a un verdict sans moyenne. On la calcule sur les photos déjà acceptées,
     * sans changer le verdict enregistré.
     */
    public void fillScore(
            Verification verification,
            Classification frontClass,
            byte[] front,
            Classification backClass,
            byte[] back) {
        if (verification.getAuthenticityVerdict() == null || verification.getAuthenticityScore() != null) {
            return;
        }
        addScore(verification, frontClass, front, true);
        addScore(verification, backClass, back, false);
        if (verification.getAuthenticityScore() != null) {
            log.info(
                    "authenticity score filled verificationId={} score={} zones={}",
                    verification.getId(),
                    verification.getAuthenticityScore(),
                    verification.getAuthenticityZoneCount());
        }
    }

    private void addScore(Verification verification, Classification classification, byte[] image, boolean required) {
        if (image == null || image.length == 0) {
            return;
        }
        try {
            Outcome outcome = assess(classification, required ? "FRONT" : "BACK", image, required);
            if (outcome.score() == null || outcome.zones() <= 0) {
                return;
            }
            verification.recordAuthenticity(
                    verification.getAuthenticityVerdict(),
                    verification.getAuthenticityDocumentId(),
                    outcome.score(),
                    outcome.zones());
        } catch (RuntimeException ex) {
            log.warn("authenticity score fill failed verificationId={}", verification.getId());
        }
    }

    /**
     * Le document IA a déjà approuvé la lecture. Le verdict visuel peut encore refuser,
     * envoyer en revue, ou laisser passer.
     */
    public Result apply(Result document, String verdict) {
        if (document == null || document.decision() != VerificationDecision.APPROVED || verdict == null) {
            return document;
        }
        return switch (verdict) {
            case "suspect" -> overlay(document, VerificationDecision.DECLINED, "authenticity_failed", SignalOutcome.FAIL);
            case "unsupported" -> overlay(
                    document, VerificationDecision.REVIEW, "authenticity_unsupported", SignalOutcome.UNAVAILABLE);
            case "a_verifier" -> overlay(
                    document, VerificationDecision.REVIEW, "authenticity_review", SignalOutcome.UNAVAILABLE);
            default -> document;
        };
    }

    private static Result overlay(Result document, VerificationDecision decision, String code, SignalOutcome outcome) {
        List<String> reasons = new ArrayList<>();
        reasons.add(code);
        if (document.reasons() != null) {
            for (String reason : document.reasons()) {
                if (!code.equals(reason)) {
                    reasons.add(reason);
                }
            }
        }
        List<Signal> signals = new ArrayList<>();
        if (document.signals() != null) {
            signals.addAll(document.signals());
        }
        signals.add(new Signal(code, outcome, null));
        return new Result(decision, List.copyOf(reasons), List.copyOf(signals), document.extractedIdentity());
    }

    /** Le verdict le plus grave des faces déjà mesurées. */
    public static String combine(String current, String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank() || rank(incoming) > rank(current)) {
            return incoming;
        }
        return current;
    }

    /** Les identifiants mesurés, recto puis verso, séparés par {@code |}. */
    public static String documents(String current, String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return incoming;
        }
        if (current.equals(incoming) || current.startsWith(incoming + "|") || current.endsWith("|" + incoming)
                || current.contains("|" + incoming + "|")) {
            return current;
        }
        String joined = current + "|" + incoming;
        return joined.length() <= 128 ? joined : incoming;
    }

    private static int rank(String verdict) {
        return switch (verdict) {
            case "suspect" -> 4;
            case "unsupported" -> 3;
            case "a_verifier" -> 2;
            case "conforme" -> 1;
            default -> 0;
        };
    }

    private static String documentId(Classification classification, String side) {
        if (classification == null || classification.code() == null || classification.code().isBlank()) {
            return null;
        }
        if ("UNKNOWN".equals(classification.code())) {
            return null;
        }
        String version = classification.version();
        String face = classification.side() == null || classification.side().isBlank() ? side : classification.side();
        if (version == null || version.isBlank() || face == null || face.isBlank()) {
            return null;
        }
        return classification.code().trim() + ":" + version.trim() + ":" + face.trim();
    }

    public record Outcome(Kind kind, String verdict, String documentId, String recaptureReason, Double score, int zones) {
        static Outcome skipped() {
            return new Outcome(Kind.SKIPPED, null, null, null, null, 0);
        }

        static Outcome unsupported(String documentId) {
            return new Outcome(Kind.UNSUPPORTED, "unsupported", documentId, null, null, 0);
        }

        static Outcome recapture(String documentId, String reason) {
            return new Outcome(Kind.RECAPTURE, "a_verifier", documentId, reason, null, 0);
        }

        static Outcome from(AuthenticityCheckResponse response) {
            String verdict = response.verdict();
            List<String> codes = response.codes() == null ? List.of() : response.codes();
            Double score = mean(response.fixed(), response.positions());
            int zones = count(response.fixed(), response.positions());
            if ("suspect".equals(verdict)) {
                return new Outcome(Kind.DECLINED, verdict, response.documentId(), null, score, zones);
            }
            if ("a_verifier".equals(verdict) && (codes.contains("alignment_failed") || codes.contains("zone_unmeasured"))) {
                String reason = codes.contains("alignment_failed") ? "alignment_failed" : "zone_unmeasured";
                return new Outcome(Kind.RECAPTURE, "a_verifier", response.documentId(), reason, score, zones);
            }
            if ("a_verifier".equals(verdict)) {
                return new Outcome(Kind.REVIEW, verdict, response.documentId(), null, score, zones);
            }
            return new Outcome(Kind.ACCEPTED, "conforme", response.documentId(), null, score, zones);
        }

        /** Moyenne des marques fixes et des positions mesurées sur cette face. */
        static Double mean(List<FixedBlock> fixed, List<PositionBlock> positions) {
            int zones = count(fixed, positions);
            if (zones == 0) {
                return null;
            }
            double sum = 0;
            if (fixed != null) {
                for (FixedBlock item : fixed) {
                    sum += item.score();
                }
            }
            if (positions != null) {
                for (PositionBlock item : positions) {
                    sum += item.iou();
                }
            }
            return sum / zones;
        }

        private static int count(List<FixedBlock> fixed, List<PositionBlock> positions) {
            return (fixed == null ? 0 : fixed.size()) + (positions == null ? 0 : positions.size());
        }
    }

    public enum Kind {
        SKIPPED,
        UNSUPPORTED,
        RECAPTURE,
        REVIEW,
        DECLINED,
        ACCEPTED
    }
}
