package com.kyc.controllers;

import com.kyc.dto.CreateVerificationRequest;
import com.kyc.dto.VerificationListResponse;
import com.kyc.dto.VerificationResponse;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.VerificationService;
import com.kyc.services.VerificationService.CreatedVerification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@SecurityRequirement(name = "bearer-api-key")
public class VerificationsController {

    private final VerificationService verificationService;

    public VerificationsController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping
    @Operation(summary = "Créer une vérification et émettre le lien hosted flow")
    public ResponseEntity<VerificationResponse> create(
            @Valid @RequestBody(required = false) CreateVerificationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CreatedVerification created = verificationService.create(CurrentApiKey.require(), body, idempotencyKey);
        HttpStatus status = created.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(created.response());
    }

    @GetMapping
    @Operation(summary = "Lister les vérifications du tenant courant")
    public VerificationListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(name = "external_id", required = false) String externalId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return verificationService.list(CurrentApiKey.require().organizationId(), status, externalId, cursor, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lire une vérification (isolée par organisation)")
    public ResponseEntity<VerificationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(
                verificationService.getForOrganization(id, CurrentApiKey.require().organizationId()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une vérification created ou pending_consent")
    public VerificationResponse cancel(@PathVariable UUID id) {
        return verificationService.cancel(CurrentApiKey.require(), id);
    }
}
