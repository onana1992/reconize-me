package com.kyc.controllers;

import com.kyc.dto.CreateVerificationRequest;
import com.kyc.dto.VerificationListResponse;
import com.kyc.dto.VerificationResponse;
import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.console.ApiKeyListItem;
import com.kyc.dto.console.ChangePasswordRequest;
import com.kyc.dto.console.InviteRequest;
import com.kyc.dto.console.MeResponse;
import com.kyc.dto.console.TeamResponse;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.CurrentConsole;
import com.kyc.services.AccountService;
import com.kyc.services.ConsoleService;
import com.kyc.services.VerificationService;
import com.kyc.services.VerificationService.CreatedVerification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/v1/console")
@Tag(name = "Console")
public class ConsoleController {

    private final ConsoleService consoleService;
    private final AccountService accountService;
    private final VerificationService verificationService;

    public ConsoleController(
            ConsoleService consoleService, AccountService accountService, VerificationService verificationService) {
        this.consoleService = consoleService;
        this.accountService = accountService;
        this.verificationService = verificationService;
    }

    @GetMapping("/me")
    @Operation(summary = "Profil session console")
    public MeResponse me() {
        return consoleService.me(CurrentConsole.require());
    }

    @PostMapping("/account/password")
    @Operation(summary = "Changer le mot de passe (session + mot de passe actuel)")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        ConsolePrincipal principal = CurrentConsole.require();
        accountService.changePassword(principal.userId(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verifications")
    @Operation(summary = "Créer une vérification depuis la console")
    public ResponseEntity<VerificationResponse> createVerification(
            @Valid @RequestBody(required = false) CreateVerificationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ConsolePrincipal principal = CurrentConsole.require();
        CreatedVerification created = verificationService.create(
                principal.organizationId(), "user", principal.userId(), body, idempotencyKey);
        HttpStatus status = created.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(created.response());
    }

    @GetMapping("/verifications")
    @Operation(summary = "Lister les vérifications du tenant")
    public VerificationListResponse listVerifications(
            @RequestParam(required = false) String status,
            @RequestParam(name = "external_id", required = false) String externalId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        ConsolePrincipal principal = CurrentConsole.require();
        return verificationService.list(principal.organizationId(), status, externalId, cursor, limit);
    }

    @GetMapping("/verifications/{id}")
    @Operation(summary = "Lire une vérification (isolée par organisation)")
    public VerificationResponse getVerification(@PathVariable UUID id) {
        ConsolePrincipal principal = CurrentConsole.require();
        return verificationService.getForOrganization(id, principal.organizationId());
    }

    @PostMapping("/verifications/{id}/cancel")
    @Operation(summary = "Annuler une vérification created ou pending_consent")
    public VerificationResponse cancelVerification(@PathVariable UUID id) {
        ConsolePrincipal principal = CurrentConsole.require();
        return verificationService.cancel(principal.organizationId(), "user", principal.userId(), id);
    }

    @GetMapping("/api-keys")
    @Operation(summary = "Lister les clés API (jamais le secret)")
    public List<ApiKeyListItem> listKeys() {
        return consoleService.listKeys(CurrentConsole.require());
    }

    @PostMapping("/api-keys")
    @Operation(summary = "Émettre une clé ky_test_ (plaintext une fois)")
    public ResponseEntity<IssuedApiKeyResponse> createKey() {
        return ResponseEntity.status(HttpStatus.CREATED).body(consoleService.createKey(CurrentConsole.require()));
    }

    @PostMapping("/api-keys/{id}/revoke")
    @Operation(summary = "Révoquer une clé (propriétaire)")
    public ResponseEntity<Void> revokeKey(@PathVariable UUID id) {
        consoleService.revokeKey(CurrentConsole.require(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/team")
    @Operation(summary = "Membres et invitations en cours")
    public TeamResponse team() {
        return consoleService.team(CurrentConsole.require());
    }

    @PostMapping("/team/invites")
    @Operation(summary = "Inviter un membre")
    public ResponseEntity<Void> invite(@Valid @RequestBody InviteRequest body) {
        consoleService.invite(CurrentConsole.require(), body.email());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
