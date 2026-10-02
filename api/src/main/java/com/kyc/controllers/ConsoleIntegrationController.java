package com.kyc.controllers;

import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.console.CreateIntegrationRequest;
import com.kyc.dto.console.IntegrationListItem;
import com.kyc.dto.console.IntegrationResponse;
import com.kyc.dto.webhook.UpsertWebhookRequest;
import com.kyc.dto.webhook.WebhookDeliveryListResponse;
import com.kyc.dto.webhook.WebhookEndpointResponse;
import com.kyc.security.CurrentConsole;
import com.kyc.services.IntegrationService;
import com.kyc.services.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/integrations")
@Tag(name = "Console")
public class ConsoleIntegrationController {

    private final IntegrationService integrations;
    private final WebhookService webhooks;

    public ConsoleIntegrationController(IntegrationService integrations, WebhookService webhooks) {
        this.integrations = integrations;
        this.webhooks = webhooks;
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

    @GetMapping("/{id}/webhook")
    @Operation(summary = "Lire le webhook de l’intégration")
    public WebhookEndpointResponse getWebhook(@PathVariable UUID id) {
        return webhooks.getConsole(CurrentConsole.require(), id);
    }

    @PutMapping("/{id}/webhook")
    @Operation(summary = "Créer ou mettre à jour le webhook")
    public WebhookEndpointResponse upsertWebhook(
            @PathVariable UUID id, @Valid @RequestBody UpsertWebhookRequest body) {
        return webhooks.upsertConsole(CurrentConsole.require(), id, body);
    }

    @DeleteMapping("/{id}/webhook")
    @Operation(summary = "Supprimer le webhook")
    public ResponseEntity<Void> deleteWebhook(@PathVariable UUID id) {
        webhooks.deleteConsole(CurrentConsole.require(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/webhook/rotate")
    @Operation(summary = "Régénérer le secret webhook")
    public WebhookEndpointResponse rotateWebhook(@PathVariable UUID id) {
        return webhooks.rotateConsole(CurrentConsole.require(), id);
    }

    @GetMapping("/{id}/webhook/deliveries")
    @Operation(summary = "Lister les livraisons webhook")
    public WebhookDeliveryListResponse listDeliveries(
            @PathVariable UUID id, @RequestParam(defaultValue = "20") int limit) {
        return webhooks.listDeliveriesConsole(CurrentConsole.require(), id, limit);
    }

    @PostMapping("/{id}/webhook/deliveries/{eventId}/retry")
    @Operation(summary = "Relancer une livraison")
    public ResponseEntity<Void> retryDelivery(@PathVariable UUID id, @PathVariable UUID eventId) {
        webhooks.retryConsole(CurrentConsole.require(), id, eventId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
