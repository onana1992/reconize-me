package com.kyc.controllers;

import com.kyc.dto.auth.AuthenticationListResponse;
import com.kyc.dto.auth.AuthenticationResponse;
import com.kyc.dto.auth.CreateAuthenticationRequest;
import com.kyc.dto.idv.ReviewRequest;
import com.kyc.dto.idv.MediaUrlResponse;
import com.kyc.enums.ProductCode;
import com.kyc.security.ApiPrincipal;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.AuthenticationService;
import com.kyc.services.IntegrationService;
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
@RequestMapping("/v1/authentications")
@Tag(name = "Authentications")
public class AuthenticationsController {

    private final AuthenticationService authentications;
    private final IntegrationService integrations;

    public AuthenticationsController(AuthenticationService authentications, IntegrationService integrations) {
        this.authentications = authentications;
        this.integrations = integrations;
    }

    @PostMapping
    @Operation(summary = "Create an authentication session")
    public ResponseEntity<AuthenticationResponse> create(
            @RequestBody(required = false) CreateAuthenticationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireProduct(
                principal.organizationId(), principal.integrationId(), ProductCode.AUTHENTICATION);
        return authentications.create(integration, "api_key", principal.apiKeyId(), body, idempotencyKey);
    }

    @GetMapping
    @Operation(summary = "List authentications for this integration")
    public AuthenticationListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(name = "external_id", required = false) String externalId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireProduct(
                principal.organizationId(), principal.integrationId(), ProductCode.AUTHENTICATION);
        return authentications.list(integration, status, externalId, cursor, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read an authentication session")
    public AuthenticationResponse get(@PathVariable UUID id) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireProduct(
                principal.organizationId(), principal.integrationId(), ProductCode.AUTHENTICATION);
        return authentications.get(integration, id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a non-terminal authentication")
    public AuthenticationResponse cancel(@PathVariable UUID id) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireProduct(
                principal.organizationId(), principal.integrationId(), ProductCode.AUTHENTICATION);
        return authentications.cancel(integration, "api_key", principal.apiKeyId(), id);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Analyst decision when the session is in review")
    public AuthenticationResponse review(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest body) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireProduct(
                principal.organizationId(), principal.integrationId(), ProductCode.AUTHENTICATION);
        return authentications.review(
                integration, "api_key", principal.apiKeyId(), id, body == null ? null : body.decision());
    }

    @GetMapping("/{id}/media/{kind}")
    @Operation(summary = "Short-lived media URL")
    public MediaUrlResponse media(@PathVariable UUID id, @PathVariable String kind) {
        ApiPrincipal principal = CurrentApiKey.require();
        var integration = integrations.requireProduct(
                principal.organizationId(), principal.integrationId(), ProductCode.AUTHENTICATION);
        return authentications.media(integration, id, kind);
    }
}
