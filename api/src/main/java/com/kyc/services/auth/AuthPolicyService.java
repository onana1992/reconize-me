package com.kyc.services.auth;

import com.kyc.entities.AuditEvent;
import com.kyc.entities.AuthenticationPolicyEntity;
import com.kyc.entities.Integration;
import com.kyc.entities.User;
import com.kyc.enums.AuthOnBorderline;
import com.kyc.enums.ProductCode;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.AuthenticationPolicyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.UserRepository;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthPolicyService {

    private final AuthenticationPolicyRepository policies;
    private final IntegrationRepository integrations;
    private final AuditEventRepository auditEvents;
    private final UserRepository users;

    public AuthPolicyService(
            AuthenticationPolicyRepository policies,
            IntegrationRepository integrations,
            AuditEventRepository auditEvents,
            UserRepository users) {
        this.policies = policies;
        this.integrations = integrations;
        this.auditEvents = auditEvents;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public AuthPolicy effective(Integration integration) {
        if (integration == null
                || !integration.isLive()
                || integration.getProduct() != ProductCode.AUTHENTICATION) {
            return AuthPolicy.factory();
        }
        return policies
                .findByIntegrationIdAndActiveTrue(integration.getId())
                .map(AuthPolicyService::toPolicy)
                .orElseGet(AuthPolicy::factory);
    }

    /** Policy copied onto the session at creation. Later publishes do not rewrite it. */
    @Transactional(readOnly = true)
    public AuthPolicy frozen(UUID integrationId, String policyVersion) {
        int version = parseVersion(policyVersion);
        if (version <= 0 || integrationId == null) {
            return AuthPolicy.factory();
        }
        return policies
                .findByIntegrationIdAndVersion(integrationId, version)
                .map(AuthPolicyService::toPolicy)
                .orElseGet(AuthPolicy::factory);
    }

    @Transactional(readOnly = true)
    public List<AuthenticationPolicyEntity> versions(UUID organizationId, UUID integrationId) {
        Integration integration = requireLive(organizationId, integrationId);
        return policies.findByIntegrationIdOrderByVersionDesc(integration.getId());
    }

    @Transactional
    public AuthenticationPolicyEntity publish(
            UUID organizationId,
            UUID userId,
            UUID integrationId,
            BigDecimal approvalThreshold,
            BigDecimal reviewThreshold,
            AuthOnBorderline onBorderline,
            int maxCaptureAttempts) {
        Integration integration = requireLive(organizationId, integrationId);
        BigDecimal approval = scale(approvalThreshold, "approval_threshold");
        if (approval.compareTo(AuthPolicy.FLOOR_APPROVAL) < 0
                || approval.compareTo(AuthPolicy.CEILING_APPROVAL) > 0) {
            throw ApiException.validation(
                    "Approval threshold is outside the allowed range",
                    List.of(new ErrorDetail("approval_threshold", "invalid")));
        }
        BigDecimal review = AuthPolicy.FLOOR_REVIEW;
        if (maxCaptureAttempts < 1 || maxCaptureAttempts > 3) {
            throw ApiException.validation(
                    "Capture attempts must be 1, 2 or 3",
                    List.of(new ErrorDetail("max_capture_attempts", "invalid")));
        }
        if (onBorderline != AuthOnBorderline.DECLINE) {
            throw ApiException.validation(
                    "Authentication has no review", List.of(new ErrorDetail("on_borderline", "invalid")));
        }
        int next = policies.findByIntegrationIdOrderByVersionDesc(integration.getId()).stream()
                .mapToInt(AuthenticationPolicyEntity::getVersion)
                .max()
                .orElse(0)
                + 1;
        policies.findByIntegrationIdAndActiveTrue(integration.getId()).ifPresent(current -> {
            current.retire();
            policies.saveAndFlush(current);
        });
        Instant now = Instant.now();
        AuthenticationPolicyEntity saved = policies.save(new AuthenticationPolicyEntity(
                UUID.randomUUID(),
                organizationId,
                integration.getId(),
                next,
                approval,
                review,
                onBorderline,
                maxCaptureAttempts,
                userId,
                now));
        auditEvents.save(new AuditEvent(
                organizationId,
                "user",
                userId,
                "authentication_policy.published",
                "authentication_policy",
                saved.getId(),
                "{\"version\":" + next + "}",
                now));
        return saved;
    }

    @Transactional(readOnly = true)
    public String publisherName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId).map(AuthPolicyService::nameOf).orElse(null);
    }

    public static AuthPolicy toPolicy(AuthenticationPolicyEntity row) {
        return new AuthPolicy(
                "auth-policy-" + row.getVersion(),
                row.getApprovalThreshold(),
                row.getReviewThreshold(),
                row.getOnBorderline(),
                row.getMaxCaptureAttempts(),
                true);
    }

    private Integration requireLive(UUID organizationId, UUID integrationId) {
        if (integrationId == null) {
            throw ApiException.validation(
                    "integration_id is required", List.of(new ErrorDetail("integration_id", "required")));
        }
        Integration integration = integrations
                .findByIdAndOrganizationId(integrationId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Integration not found"));
        if (integration.getProduct() != ProductCode.AUTHENTICATION || !integration.isLive()) {
            throw ApiException.validation(
                    "Invalid integration", List.of(new ErrorDetail("integration_id", "invalid")));
        }
        return integration;
    }

    private static BigDecimal scale(BigDecimal value, String field) {
        if (value == null) {
            throw ApiException.validation(field + " is required", List.of(new ErrorDetail(field, "required")));
        }
        if (value.scale() > 3) {
            throw ApiException.validation(
                    field + " must have at most 3 decimal places", List.of(new ErrorDetail(field, "invalid")));
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    static int parseVersion(String policyVersion) {
        if (policyVersion == null || !policyVersion.startsWith("auth-policy-")) {
            return 0;
        }
        try {
            return Integer.parseInt(policyVersion.substring("auth-policy-".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String nameOf(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isBlank() ? user.getEmail() : name;
    }
}
