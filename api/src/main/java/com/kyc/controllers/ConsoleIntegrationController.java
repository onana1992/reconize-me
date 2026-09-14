package com.kyc.controllers;

import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.console.CreateIntegrationRequest;
import com.kyc.dto.console.IntegrationListItem;
import com.kyc.dto.console.IntegrationResponse;
import com.kyc.security.CurrentConsole;
import com.kyc.services.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/integrations")
@Tag(name = "Console")
public class ConsoleIntegrationController {

    private final IntegrationService integrations;

    public ConsoleIntegrationController(IntegrationService integrations) {
        this.integrations = integrations;
    }

    @GetMapping
    @Operation(summary = "Lister les intégrations de l’organisation")
    public List<IntegrationListItem> list() {
        return integrations.list(CurrentConsole.require());
    }

    @PostMapping
    @Operation(summary = "Créer une intégration (live bloqué sans crédit)")
    public ResponseEntity<IntegrationResponse> create(@RequestBody(required = false) CreateIntegrationRequest body) {
        String name = body == null ? null : body.name();
        String mode = body == null ? null : body.mode();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(integrations.create(CurrentConsole.require(), name, mode));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche d’une intégration")
    public IntegrationResponse get(@PathVariable UUID id) {
        return integrations.get(CurrentConsole.require(), id);
    }

    @PostMapping("/{id}/api-keys")
    @Operation(summary = "Émettre une clé sur cette intégration (plaintext une fois)")
    public ResponseEntity<IssuedApiKeyResponse> createKey(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(integrations.issueKey(CurrentConsole.require(), id));
    }
}
