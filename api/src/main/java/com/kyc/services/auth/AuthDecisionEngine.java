package com.kyc.services.auth;

import com.kyc.enums.AuthenticationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Pure decision for auth-2. No Spring, no AWS. Callers withhold Search candidates when presence is
 * below 0.80 (upstream recapture); this engine never invents an {@code approved}.
 */
public final class AuthDecisionEngine {

    public record Candidate(String faceId, float similarityPoints) {}

    public record Result(
            AuthenticationStatus status, List<String> reasons, BigDecimal matchScore, String failureReason) {

        public static Result declined(String reason) {
            return new Result(AuthenticationStatus.DECLINED, List.of(reason), null, null);
        }

        public static Result declined(String reason, BigDecimal matchScore) {
            return new Result(AuthenticationStatus.DECLINED, List.of(reason), matchScore, null);
        }

        public static Result approved(BigDecimal matchScore) {
            return new Result(
                    AuthenticationStatus.APPROVED,
                    List.of("liveness_pass", "face_match_pass"),
                    matchScore,
                    null);
        }

        public static Result failed(String failureReason) {
            return new Result(AuthenticationStatus.FAILED, List.of(), null, failureReason);
        }
    }

    /**
     * @param enrollmentActive status of the frozen enrollment at decision time
     * @param frozenFaceId FaceId copied onto the session at creation
     * @param presentationAttack frank liveness attack (photo / screen / replay)
     * @param livenessPass presence ≥ policy floor (0.80); when false and not an attack, callers must
     *     not supply candidates (upstream recapture)
     * @param candidates Search results already filtered by the provider threshold; may be empty
     * @param providerFailure typed outage ({@code provider_timeout}, …); never yields approved
     * @param policy frozen session policy
     */
    public Result decide(
            boolean enrollmentActive,
            String frozenFaceId,
            boolean presentationAttack,
            boolean livenessPass,
            List<Candidate> candidates,
            String providerFailure,
            AuthPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (providerFailure != null && !providerFailure.isBlank()) {
            return Result.failed(providerFailure.trim());
        }
        if (!enrollmentActive) {
            return Result.declined("enrollment_inactive");
        }
        if (presentationAttack) {
            return Result.declined("presentation_attack");
        }
        if (!livenessPass) {
            // Presence below gate: comparison was not run. Treat as frank failure if forced here.
            return Result.declined("presentation_attack");
        }
        Objects.requireNonNull(frozenFaceId, "frozenFaceId");
        Candidate match = null;
        if (candidates != null) {
            for (Candidate candidate : candidates) {
                if (candidate == null || candidate.faceId() == null) {
                    continue;
                }
                if (frozenFaceId.equals(candidate.faceId())) {
                    match = candidate;
                    break;
                }
            }
        }
        if (match == null) {
            return Result.declined("face_match_fail");
        }
        BigDecimal score = toScore(match.similarityPoints());
        if (score.compareTo(policy.approvalThreshold()) < 0) {
            return Result.declined("face_match_fail", score);
        }
        return Result.approved(score);
    }

    /** Similarity points [0, 100] → score [0, 1], three decimals, half-up. */
    public static BigDecimal toScore(float similarityPoints) {
        return BigDecimal.valueOf(similarityPoints)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
    }
}
