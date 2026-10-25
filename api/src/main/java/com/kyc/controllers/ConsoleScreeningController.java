package com.kyc.controllers;

import com.kyc.dto.aml.ConsoleCreateScreeningRequest;
import com.kyc.dto.aml.CreateScreeningRequest;
import com.kyc.dto.aml.DispositionRequest;
import com.kyc.dto.aml.ScreeningListResponse;
import com.kyc.dto.aml.ScreeningResponse;
import com.kyc.entities.Integration;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.CurrentConsole;
import com.kyc.security.Permission;
import com.kyc.services.IntegrationService;
import com.kyc.services.ScreeningService;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/screenings")
public class ConsoleScreeningController {

    private final ScreeningService screenings;
    private final IntegrationService integrations;

    public ConsoleScreeningController(ScreeningService screenings, IntegrationService integrations) {
        this.screenings = screenings;
        this.integrations = integrations;
    }

    @PostMapping
    public ResponseEntity<ScreeningResponse> create(@RequestBody(required = false) ConsoleCreateScreeningRequest body) {
        var principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_WRITE);
        if (body == null || body.integrationId() == null) {
            throw ApiException.validation(
                    "integration_id is required", List.of(new ErrorDetail("integration_id", "required")));
        }
        Integration integration = integrations.requireInOrg(principal.organizationId(), body.integrationId());
        return screenings.create(
                integration,
                "user",
                principal.userId(),
                new CreateScreeningRequest(
                        null,
                        body.firstName(),
                        null,
                        body.lastName(),
                        body.dateOfBirth(),
                        body.nationality(),
                        null,
                        null,
                        null,
                        null),
                null);
    }

    @GetMapping
    public ScreeningListResponse list(
            @RequestParam(name = "integration_id", required = false) UUID integrationId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        var principal = CurrentConsole.require();
        requireRead(principal);
        return screenings.listOrg(principal.organizationId(), integrationId, outcome, cursor, limit);
    }

    @GetMapping("/{id}")
    public ScreeningResponse get(@PathVariable UUID id) {
        var principal = CurrentConsole.require();
        requireRead(principal);
        return screenings.getInOrg(principal.organizationId(), id);
    }

    @PostMapping("/{id}/hits/{hitId}/disposition")
    public ScreeningResponse dispose(
            @PathVariable UUID id, @PathVariable UUID hitId, @RequestBody(required = false) DispositionRequest body) {
        var principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_WRITE);
        return screenings.disposeInOrg(
                principal.organizationId(), "user", principal.userId(), id, hitId, body, null);
    }

    private static void requireRead(ConsolePrincipal principal) {
        if (!ConsoleAuth.allows(principal.role(), Permission.VERIFICATION_READ)
                && !ConsoleAuth.allows(principal.role(), Permission.API_KEY_READ)) {
            throw ApiException.forbidden("forbidden", "Insufficient permissions");
        }
    }
}
