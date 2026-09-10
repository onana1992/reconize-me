package com.kyc.controllers;

import com.kyc.dto.idv.CreateVerificationRequest;
import com.kyc.dto.idv.MediaUrlResponse;
import com.kyc.dto.idv.ReviewRequest;
import com.kyc.dto.idv.VerificationListResponse;
import com.kyc.dto.idv.VerificationResponse;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.CurrentConsole;
import com.kyc.security.Permission;
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
@RequestMapping("/v1/console/verifications")
@Tag(name = "Console")
public class ConsoleVerificationController {

    private final VerificationService verifications;

    public ConsoleVerificationController(VerificationService verifications) {
        this.verifications = verifications;
    }

    @GetMapping
    @Operation(summary = "Liste des vérifications")
    public VerificationListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_READ);
        return verifications.list(principal.organizationId(), status, cursor, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche vérification")
    public VerificationResponse get(@PathVariable UUID id) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_READ);
        return verifications.get(principal.organizationId(), id);
    }

    @PostMapping
    @Operation(summary = "Créer une vérification")
    public ResponseEntity<VerificationResponse> create(
            @RequestBody(required = false) CreateVerificationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_WRITE);
        return verifications.create(principal.organizationId(), "user", principal.userId(), body, idempotencyKey);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une vérification")
    public VerificationResponse cancel(@PathVariable UUID id) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_WRITE);
        return verifications.cancel(principal.organizationId(), "user", principal.userId(), id);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Trancher une vérification en revue")
    public VerificationResponse review(@PathVariable UUID id, @RequestBody ReviewRequest body) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_WRITE);
        return verifications.review(
                principal.organizationId(), "user", principal.userId(), id, body == null ? null : body.decision());
    }

    @GetMapping("/{id}/media/{kind}")
    @Operation(summary = "URL signée d’un média accepté")
    public MediaUrlResponse media(@PathVariable UUID id, @PathVariable String kind) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_READ);
        return verifications.mediaUrl(principal.organizationId(), id, kind);
    }
}
