package com.kyc.controllers;

import com.kyc.dto.idv.CreateVerificationRequest;
import com.kyc.dto.idv.MediaUrlResponse;
import com.kyc.dto.idv.ReviewRequest;
import com.kyc.dto.idv.VerificationListResponse;
import com.kyc.dto.idv.VerificationResponse;
import com.kyc.security.ApiPrincipal;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.VerificationService;
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
@RequestMapping("/v1/verifications")
@Tag(name = "Verifications")
public class VerificationsController {

    private final VerificationService verifications;

    public VerificationsController(VerificationService verifications) {
        this.verifications = verifications;
    }

    @PostMapping
    @Operation(summary = "Créer une vérification sandbox")
    public ResponseEntity<VerificationResponse> create(
            @RequestBody(required = false) CreateVerificationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ApiPrincipal principal = CurrentApiKey.require();
        return verifications.create(principal.organizationId(), "api_key", principal.apiKeyId(), body, idempotencyKey);
    }

    @GetMapping
    @Operation(summary = "Lister les vérifications du tenant")
    public VerificationListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        ApiPrincipal principal = CurrentApiKey.require();
        return verifications.list(principal.organizationId(), status, cursor, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche vérification")
    public VerificationResponse get(@PathVariable UUID id) {
        ApiPrincipal principal = CurrentApiKey.require();
        return verifications.get(principal.organizationId(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une vérification")
    public VerificationResponse cancel(@PathVariable UUID id) {
        ApiPrincipal principal = CurrentApiKey.require();
        return verifications.cancel(principal.organizationId(), "api_key", principal.apiKeyId(), id);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Trancher une vérification en revue")
    public VerificationResponse review(@PathVariable UUID id, @RequestBody ReviewRequest body) {
        ApiPrincipal principal = CurrentApiKey.require();
        return verifications.review(
                principal.organizationId(), "api_key", principal.apiKeyId(), id, body == null ? null : body.decision());
    }

    @GetMapping("/{id}/media/{kind}")
    @Operation(summary = "URL signée d’un média accepté")
    public MediaUrlResponse media(@PathVariable UUID id, @PathVariable String kind) {
        ApiPrincipal principal = CurrentApiKey.require();
        return verifications.mediaUrl(principal.organizationId(), id, kind);
    }
}
