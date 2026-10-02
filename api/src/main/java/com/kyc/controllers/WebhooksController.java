package com.kyc.controllers;

import com.kyc.dto.webhook.UpsertWebhookRequest;
import com.kyc.dto.webhook.WebhookEndpointResponse;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks")
@Tag(name = "Webhooks")
public class WebhooksController {

    private final WebhookService webhooks;

    public WebhooksController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PutMapping
    @Operation(summary = "Créer ou mettre à jour l’endpoint webhook de l’intégration")
    public WebhookEndpointResponse upsert(@Valid @RequestBody UpsertWebhookRequest body) {
        return webhooks.upsertBearer(CurrentApiKey.require(), body);
    }

    @PostMapping
    @Operation(summary = "Alias upsert (CDC)")
    public WebhookEndpointResponse upsertPost(@Valid @RequestBody UpsertWebhookRequest body) {
        return upsert(body);
    }

    @GetMapping
    @Operation(summary = "Lire l’endpoint webhook (secret_prefix seulement)")
    public WebhookEndpointResponse get() {
        return webhooks.getBearer(CurrentApiKey.require());
    }

    @DeleteMapping
    @Operation(summary = "Supprimer l’endpoint webhook")
    public ResponseEntity<Void> delete() {
        webhooks.deleteBearer(CurrentApiKey.require());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rotate")
    @Operation(summary = "Régénérer le secret webhook (montré une fois)")
    public WebhookEndpointResponse rotate() {
        return webhooks.rotateBearer(CurrentApiKey.require());
    }

    @PostMapping("/deliveries/{eventId}/retry")
    @Operation(summary = "Relancer une livraison")
    public ResponseEntity<Void> retry(@PathVariable UUID eventId) {
        webhooks.retryBearer(CurrentApiKey.require(), eventId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
