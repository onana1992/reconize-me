package com.kyc.services.aml;

import com.kyc.entities.AmlPolicyEntity;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Integration;
import com.kyc.enums.AmlCategory;
import com.kyc.enums.AmlOnMatch;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.repositories.AmlPolicyRepository;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AmlPolicyService {

    private final AmlPolicyRepository policies;
    private final IntegrationRepository integrations;
    private final AuditEventRepository auditEvents;

    public AmlPolicyService(
            AmlPolicyRepository policies, IntegrationRepository integrations, AuditEventRepository auditEvents) {
        this.policies = policies;
        this.integrations = integrations;
        this.auditEvents = auditEvents;
    }

    @Transactional(readOnly = true)
    public AmlPolicy effective(UUID organizationId) {
        return policies.findByOrganizationIdAndActiveTrue(organizationId)
                .map(AmlPolicyService::toPolicy)
                .orElseGet(AmlPolicy::factory);
    }

    @Transactional(readOnly = true)
    public List<AmlPolicyEntity> versions(UUID organizationId) {
        return policies.findByOrganizationIdOrderByVersionDesc(organizationId);
    }

    @Transactional
    public AmlPolicyEntity publish(UUID organizationId, UUID actorId, PublishCommand command) {
        BigDecimal review = scale(command.reviewThreshold(), "review_threshold");
        BigDecimal confirm = scale(command.confirmThreshold(), "confirm_threshold");
        if (review.compareTo(new BigDecimal("0.500")) < 0 || review.compareTo(new BigDecimal("0.850")) > 0) {
            throw ApiException.validation("Invalid review threshold", List.of(new ErrorDetail("review_threshold", "invalid")));
        }
        if (confirm.compareTo(new BigDecimal("0.900")) < 0
                || confirm.compareTo(BigDecimal.ONE) > 0
                || confirm.compareTo(review) <= 0) {
            throw ApiException.validation(
                    "Invalid confirm threshold", List.of(new ErrorDetail("confirm_threshold", "invalid")));
        }
        Set<AmlCategory> categories = parseCategories(command.categories());
        if (!categories.contains(AmlCategory.SANCTION)) {
            throw ApiException.validation("Sanction is required", List.of(new ErrorDetail("categories", "required")));
        }
        AmlOnMatch onMatch = command.onMatch() == null ? AmlOnMatch.REVIEW : command.onMatch();
        UUID testIntegration = integration(organizationId, command.testIntegrationId(), IntegrationMode.TEST);
        UUID liveIntegration = integration(organizationId, command.liveIntegrationId(), IntegrationMode.LIVE);
        int next = policies.findTopByOrganizationIdOrderByVersionDesc(organizationId)
                .map(row -> row.getVersion() + 1)
                .orElse(1);
        policies.findByOrganizationIdAndActiveTrue(organizationId).ifPresent(current -> {
            current.retire();
            policies.save(current);
        });
        Instant now = Instant.now();
        AmlPolicyEntity saved = policies.save(new AmlPolicyEntity(
                UUID.randomUUID(),
                organizationId,
                next,
                review,
                confirm,
                join(categories),
                command.attachToIdentity(),
                onMatch,
                testIntegration,
                liveIntegration,
                actorId,
                now));
        auditEvents.save(new AuditEvent(
                organizationId,
                "user",
                actorId,
                "aml_policy.published",
                "aml_policy",
                saved.getId(),
                "{\"version\":" + next + "}",
                now));
        return saved;
    }

    public static AmlPolicy toPolicy(AmlPolicyEntity row) {
        return new AmlPolicy(
                "aml-policy-" + row.getVersion(),
                row.getReviewThreshold(),
                row.getConfirmThreshold(),
                parseStored(row.getCategories()),
                row.isAttachToIdentity(),
                row.getOnMatch(),
                row.getTestIntegrationId(),
                row.getLiveIntegrationId());
    }

    private UUID integration(UUID organizationId, UUID id, IntegrationMode mode) {
        if (id == null) {
            return null;
        }
        Integration integration = integrations
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.validation(
                        "Integration not found", List.of(new ErrorDetail("integration_id", "invalid"))));
        if (integration.getProduct() != ProductCode.AML || integration.getMode() != mode) {
            throw ApiException.validation("Integration mode", List.of(new ErrorDetail("integration_id", "invalid")));
        }
        return id;
    }

    private static Set<AmlCategory> parseCategories(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw ApiException.validation("Categories are required", List.of(new ErrorDetail("categories", "required")));
        }
        Set<AmlCategory> categories = EnumSet.noneOf(AmlCategory.class);
        for (String item : raw) {
            AmlCategory category = AmlCategory.parse(item);
            if (category == null) {
                throw ApiException.validation("Unknown category", List.of(new ErrorDetail("categories", "invalid")));
            }
            categories.add(category);
        }
        return categories;
    }

    private static Set<AmlCategory> parseStored(String raw) {
        Set<AmlCategory> categories = EnumSet.noneOf(AmlCategory.class);
        for (String item : raw.split(",")) {
            AmlCategory category = AmlCategory.parse(item);
            if (category != null) {
                categories.add(category);
            }
        }
        return categories;
    }

    private static String join(Set<AmlCategory> categories) {
        List<String> names = new ArrayList<>();
        for (AmlCategory category : AmlCategory.values()) {
            if (categories.contains(category)) {
                names.add(category.json());
            }
        }
        return String.join(",", names);
    }

    private static BigDecimal scale(BigDecimal value, String field) {
        if (value == null) {
            throw ApiException.validation("Threshold is required", List.of(new ErrorDetail(field, "required")));
        }
        return value.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    public record PublishCommand(
            BigDecimal reviewThreshold,
            BigDecimal confirmThreshold,
            List<String> categories,
            boolean attachToIdentity,
            AmlOnMatch onMatch,
            UUID testIntegrationId,
            UUID liveIntegrationId) {}
}
