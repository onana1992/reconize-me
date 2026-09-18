package com.kyc.services;

import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.console.ApiKeyListItem;
import com.kyc.dto.console.IntegrationListItem;
import com.kyc.dto.console.IntegrationResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Integration;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.Permission;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationService {

    private final IntegrationRepository integrations;
    private final ApiKeyRepository apiKeys;
    private final AuditEventRepository auditEvents;
    private final ApiKeyIssuer apiKeyIssuer;
    private final CreditService credits;

    public IntegrationService(
            IntegrationRepository integrations,
            ApiKeyRepository apiKeys,
            AuditEventRepository auditEvents,
            ApiKeyIssuer apiKeyIssuer,
            CreditService credits) {
        this.integrations = integrations;
        this.apiKeys = apiKeys;
        this.auditEvents = auditEvents;
        this.apiKeyIssuer = apiKeyIssuer;
        this.credits = credits;
    }

    @Transactional
    public IssuedApiKeyResponse issueTestKey(UUID organizationId, UUID userId) {
        Integration test = integrations
                .findFirstByOrganizationIdAndProductAndModeOrderByCreatedAtAsc(
                        organizationId, Integration.PRODUCT_IDENTITY, Integration.MODE_TEST)
                .orElseThrow(() -> ApiException.conflict("no_integration", "Create an integration first"));
        requireNoKey(test.getId());
        return apiKeyIssuer.issue(test, userId);
    }

    @Transactional(readOnly = true)
    public List<IntegrationListItem> list(ConsolePrincipal principal) {
        return integrations.findByOrganizationIdOrderByCreatedAtAsc(principal.organizationId()).stream()
                .map(row -> new IntegrationListItem(
                        row.getId(), row.getProduct(), row.getMode(), row.getName(), row.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationResponse get(ConsolePrincipal principal, UUID id) {
        Integration integration = requireInOrg(principal.organizationId(), id);
        boolean canReadKeys = ConsoleAuth.allows(principal.role(), Permission.API_KEY_READ);
        List<ApiKeyListItem> keys = canReadKeys ? keysOf(integration.getId()) : List.of();
        return toResponse(integration, keys, null);
    }

    @Transactional
    public IntegrationResponse create(ConsolePrincipal principal, String name, String mode) {
        ConsoleAuth.require(principal, Permission.API_KEY_WRITE);
        String normalized = normalizeMode(mode);
        if (Integration.MODE_LIVE.equals(normalized)) {
            if (!credits.coversUnit(principal.organizationId())) {
                throw ApiException.forbidden("insufficient_credit", "Live integrations require a credit balance");
            }
        }
        String resolvedName = requireUniqueName(principal.organizationId(), name);
        Integration saved = saveNew(principal.organizationId(), normalized, resolvedName, principal.userId());
        IssuedApiKeyResponse issued = apiKeyIssuer.issue(saved, principal.userId());
        return toResponse(saved, keysOf(saved.getId()), issued.key());
    }

    @Transactional
    public IssuedApiKeyResponse issueKey(ConsolePrincipal principal, UUID integrationId) {
        ConsoleAuth.require(principal, Permission.API_KEY_WRITE);
        Integration integration = requireInOrg(principal.organizationId(), integrationId);
        requireNoKey(integration.getId());
        return apiKeyIssuer.issue(integration, principal.userId());
    }

    @Transactional(readOnly = true)
    public Integration requireInOrg(UUID organizationId, UUID integrationId) {
        return integrations
                .findByIdAndOrganizationId(integrationId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Integration not found"));
    }

    @Transactional(readOnly = true)
    public Integration resolveForCreate(UUID organizationId, UUID integrationId) {
        if (integrationId == null) {
            throw ApiException.validation(
                    "integration_id is required", List.of(new ErrorDetail("integration_id", "required")));
        }
        return requireInOrg(organizationId, integrationId);
    }

    private void requireNoKey(UUID integrationId) {
        if (apiKeys.existsByIntegrationId(integrationId)) {
            throw ApiException.conflict("key_exists", "This integration already has a key");
        }
    }

    private String requireUniqueName(UUID organizationId, String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.validation("Name is required", List.of(new ErrorDetail("name", "required")));
        }
        String resolvedName = name.trim();
        if (resolvedName.length() > 128) {
            throw ApiException.validation("Invalid name", List.of(new ErrorDetail("name", "size")));
        }
        if (integrations.existsByOrganizationIdAndProductAndNameIgnoreCase(
                organizationId, Integration.PRODUCT_IDENTITY, resolvedName)) {
            throw ApiException.conflict("name_taken", "An integration with this name already exists");
        }
        return resolvedName;
    }

    private Integration saveNew(UUID organizationId, String mode, String name, UUID actorId) {
        Instant now = Instant.now();
        Integration saved = integrations.save(new Integration(
                UUID.randomUUID(), organizationId, Integration.PRODUCT_IDENTITY, mode, name, now));
        auditEvents.save(new AuditEvent(
                organizationId,
                actorId == null ? "system" : "user",
                actorId,
                "integration.created",
                "integration",
                saved.getId(),
                "{}",
                now));
        return saved;
    }

    private List<ApiKeyListItem> keysOf(UUID integrationId) {
        return apiKeys.findByIntegrationIdOrderByCreatedAtDesc(integrationId).stream()
                .map(key -> new ApiKeyListItem(
                        key.getId(), key.getIntegrationId(), key.getKeyPrefix(), key.getCreatedAt(), key.isRevoked()))
                .toList();
    }

    private static IntegrationResponse toResponse(Integration integration, List<ApiKeyListItem> keys, String key) {
        return new IntegrationResponse(
                integration.getId(),
                integration.getProduct(),
                integration.getMode(),
                integration.getName(),
                integration.getCreatedAt(),
                keys,
                key);
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return Integration.MODE_TEST;
        }
        String value = mode.trim().toLowerCase();
        if (Integration.MODE_TEST.equals(value) || Integration.MODE_LIVE.equals(value)) {
            return value;
        }
        throw ApiException.validation("Invalid mode", List.of(new ErrorDetail("mode", "invalid")));
    }
}
