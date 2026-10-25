package com.kyc.controllers;

import com.kyc.dto.aml.CreateScreeningRequest;
import com.kyc.dto.aml.DispositionRequest;
import com.kyc.dto.aml.ScreeningListResponse;
import com.kyc.dto.aml.ScreeningResponse;
import com.kyc.security.ApiPrincipal;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.IntegrationService;
import com.kyc.services.ScreeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/screenings")
@Tag(name = "Screenings")
public class ScreeningsController {

    private final ScreeningService screenings;
    private final IntegrationService integrations;

    public ScreeningsController(ScreeningService screenings, IntegrationService integrations) {
        this.screenings = screenings;
        this.integrations = integrations;
    }

    @PostMapping
    @Operation(summary = "Créer un criblage")
    public ResponseEntity<ScreeningResponse> create(
            @RequestBody(required = false) CreateScreeningRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireInOrg(principal.organizationId(), principal.integrationId());
        return screenings.create(integration, "api_key", principal.apiKeyId(), body, idempotencyKey);
    }

    @GetMapping
    @Operation(summary = "Lister les criblages de l’intégration")
    public ScreeningListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String outcome,
            @RequestParam(name = "external_id", required = false) String externalId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireInOrg(principal.organizationId(), principal.integrationId());
        return screenings.list(integration, status, outcome, externalId, cursor, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lire un criblage")
    public ScreeningResponse get(@PathVariable UUID id) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireInOrg(principal.organizationId(), principal.integrationId());
        return screenings.get(integration, id);
    }

    @PostMapping("/{id}/hits/{hitId}/disposition")
    @Operation(summary = "Qualifier une correspondance")
    public ScreeningResponse dispose(
            @PathVariable UUID id, @PathVariable UUID hitId, @RequestBody(required = false) DispositionRequest body) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireInOrg(principal.organizationId(), principal.integrationId());
        return screenings.dispose(integration, "api_key", principal.apiKeyId(), id, hitId, body);
    }
}
