package com.kyc.controllers;

import com.kyc.dto.auth.AuthenticationListResponse;
import com.kyc.dto.auth.AuthenticationResponse;
import com.kyc.dto.auth.ConsoleCreateAuthenticationRequest;
import com.kyc.dto.auth.CreateAuthenticationRequest;
import com.kyc.dto.idv.MediaUrlResponse;
import com.kyc.dto.idv.ReviewRequest;
import com.kyc.enums.ProductCode;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.CurrentConsole;
import com.kyc.security.Permission;
import com.kyc.services.AuthenticationService;
import com.kyc.services.IntegrationService;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
@RequestMapping("/v1/console/authentications")
@Tag(name = "Console")
public class ConsoleAuthenticationsController {

    private final AuthenticationService authentications;
    private final IntegrationService integrations;

    public ConsoleAuthenticationsController(AuthenticationService authentications, IntegrationService integrations) {
        this.authentications = authentications;
        this.integrations = integrations;
    }

    @GetMapping
    @Operation(summary = "Liste des authentifications")
    public AuthenticationListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(value = "integration_id", required = false) UUID integrationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_READ);
        return authentications.listForOrganization(principal.organizationId(), status, integrationId, cursor, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche authentification")
    public AuthenticationResponse get(@PathVariable UUID id) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_READ);
        return authentications.getForOrganization(principal.organizationId(), id);
    }

    @PostMapping
    @Operation(summary = "Créer une authentification et son lien hébergé")
    public ResponseEntity<AuthenticationResponse> create(
            @RequestBody(required = false) ConsoleCreateAuthenticationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_WRITE);
        var integration = integrations.resolveForCreate(
                principal.organizationId(), body == null ? null : body.integrationId());
        if (integration.getProduct() != ProductCode.AUTHENTICATION) {
            throw ApiException.validation(
                    "Invalid integration", List.of(new ErrorDetail("integration_id", "invalid")));
        }
        return authentications.create(
                integration,
                "user",
                principal.userId(),
                new CreateAuthenticationRequest(body == null ? null : body.externalId(), null, null),
                idempotencyKey);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Revue analyste")
    public AuthenticationResponse review(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest body) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_WRITE);
        AuthenticationResponse current = authentications.getForOrganization(principal.organizationId(), id);
        var integration = integrations.resolveForCreate(principal.organizationId(), current.integrationId());
        return authentications.review(
                integration, "user", principal.userId(), id, body == null ? null : body.decision());
    }

    @GetMapping("/{id}/media/{kind}")
    @Operation(summary = "URL courte du selfie ou de la référence")
    public MediaUrlResponse media(@PathVariable UUID id, @PathVariable String kind) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_READ);
        return authentications.mediaForOrganization(principal.organizationId(), id, kind);
    }
}
