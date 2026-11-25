package com.kyc.services;

import com.kyc.dto.auth.EnrollmentResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Enrollment;
import com.kyc.entities.Integration;
import com.kyc.entities.Verification;
import com.kyc.enums.EnrollmentSource;
import com.kyc.enums.EnrollmentStatus;
import com.kyc.enums.Enums;
import com.kyc.enums.ProductCode;
import com.kyc.enums.VerificationDecision;
import com.kyc.ports.FaceCollectionClient;
import com.kyc.ports.FaceCollectionClient.IndexResult;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.EnrollmentRepository;
import com.kyc.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository enrollments;
    private final IdvPolicyService policies;
    private final FaceCollectionClient faces;
    private final AuditEventRepository auditEvents;

    public EnrollmentService(
            EnrollmentRepository enrollments,
            IdvPolicyService policies,
            FaceCollectionClient faces,
            AuditEventRepository auditEvents) {
        this.enrollments = enrollments;
        this.policies = policies;
        this.faces = faces;
        this.auditEvents = auditEvents;
    }

    /**
     * Best-effort after a live IDV {@code approved}. Failures never change the identity decision.
     * Logs omit image bytes, FaceId, and external_id.
     */
    @Transactional
    public void afterIdvApproved(
            Verification verification,
            Integration identity,
            List<String> reasons,
            byte[] livenessReference) {
        if (verification == null || identity == null) {
            return;
        }
        if (identity.getProduct() != ProductCode.IDENTITY || !identity.isLive()) {
            return;
        }
        if (verification.getDecision() != VerificationDecision.APPROVED) {
            return;
        }
        if (verification.getExternalId() == null || verification.getExternalId().isBlank()) {
            return;
        }
        if (livenessReference == null || livenessReference.length == 0) {
            return;
        }
        if (reasons == null || !reasons.contains("liveness_pass") || !reasons.contains("face_match_pass")) {
            return;
        }
        if (!policies.applied(verification.getPolicyId()).enrollmentEnabled()) {
            return;
        }
        String collectionId;
        try {
            collectionId = faces.ensureCollection(verification.getOrganizationId());
        } catch (RuntimeException e) {
            log.info("enrollment.collection_unavailable verificationId={}", verification.getId());
            return;
        }
        UUID enrollmentId = UUID.randomUUID();
        Instant now = Instant.now();
        IndexResult indexed = faces.indexFaces(collectionId, livenessReference, enrollmentId.toString());
        if (!(indexed instanceof IndexResult.Ok ok)) {
            String reason = indexed instanceof IndexResult.Failed failed ? failed.reason() : "index_failed";
            log.info(
                    "enrollment.index_failed verificationId={} reason={}",
                    verification.getId(),
                    reason);
            return;
        }

        Enrollment previous = enrollments
                .findByOrganizationIdAndExternalIdAndStatus(
                        verification.getOrganizationId(),
                        verification.getExternalId(),
                        EnrollmentStatus.ACTIVE)
                .orElse(null);
        if (previous != null) {
            try {
                faces.deleteFaces(collectionId, List.of(previous.getFaceId()));
            } catch (RuntimeException e) {
                log.info("enrollment.replace_delete_failed verificationId={}", verification.getId());
                try {
                    faces.deleteFaces(collectionId, List.of(ok.faceId()));
                } catch (RuntimeException ignored) {
                    // leave the unused vector; no active row published for it
                }
                return;
            }
            previous.retire(now);
            enrollments.saveAndFlush(previous);
            audit(
                    verification.getOrganizationId(),
                    "enrollment.retired",
                    previous.getId(),
                    now);
        }

        Enrollment created = new Enrollment(
                enrollmentId,
                verification.getOrganizationId(),
                verification.getExternalId(),
                EnrollmentSource.IDV,
                verification.getId(),
                ok.faceId(),
                collectionId,
                now);
        enrollments.save(created);
        audit(verification.getOrganizationId(), "enrollment.created", created.getId(), now);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse lookup(UUID organizationId, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw ApiException.validation(
                    "external_id is required",
                    List.of(new com.kyc.web.ErrorDetail("external_id", "required")));
        }
        return enrollments
                .findByOrganizationIdAndExternalIdAndStatus(organizationId, externalId.trim(), EnrollmentStatus.ACTIVE)
                .map(this::toResponse)
                .orElseGet(() -> enrollments
                        .findByOrganizationIdAndExternalIdOrderByCreatedAtDesc(organizationId, externalId.trim())
                        .stream()
                        .filter(row -> row.getStatus() != EnrollmentStatus.ACTIVE)
                        .findFirst()
                        .map(this::toResponse)
                        .orElseGet(EnrollmentResponse::absent));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> list(UUID organizationId) {
        return enrollments
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId, PageRequest.of(0, 100))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse get(UUID organizationId, UUID id) {
        return toResponse(requireInOrg(organizationId, id));
    }

    @Transactional
    public EnrollmentResponse revoke(UUID organizationId, UUID id) {
        Enrollment enrollment = requireInOrg(organizationId, id);
        if (enrollment.getStatus() == EnrollmentStatus.REVOKED) {
            return toResponse(enrollment);
        }
        if (enrollment.getStatus() == EnrollmentStatus.RETIRED) {
            throw ApiException.conflict("invalid_status", "Retired enrollments cannot be revoked");
        }
        Instant now = Instant.now();
        try {
            faces.deleteFaces(enrollment.getCollectionId(), List.of(enrollment.getFaceId()));
        } catch (RuntimeException e) {
            log.info("enrollment.revoke_delete_failed enrollmentId={}", enrollment.getId());
            throw ApiException.dependencyUnavailable("Face collection unavailable");
        }
        enrollment.revoke(now);
        enrollments.save(enrollment);
        audit(organizationId, "enrollment.revoked", enrollment.getId(), now);
        return toResponse(enrollment);
    }

    @Transactional(readOnly = true)
    public void rejectManualCreate() {
        throw ApiException.conflict("enrollment_requires_idv", "Enrollments are created from live identity only");
    }

    private Enrollment requireInOrg(UUID organizationId, UUID id) {
        return enrollments
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Enrollment not found"));
    }

    private EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                Enums.json(enrollment.getStatus()),
                Enums.json(enrollment.getSource()),
                enrollment.getExternalId(),
                enrollment.getSourceVerificationId(),
                enrollment.getCreatedAt(),
                enrollment.getEndedAt());
    }

    private void audit(UUID organizationId, String action, UUID enrollmentId, Instant now) {
        auditEvents.save(new AuditEvent(
                organizationId, "system", null, action, "enrollment", enrollmentId, "{}", now));
    }
}
