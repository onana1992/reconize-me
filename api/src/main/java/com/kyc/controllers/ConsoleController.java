package com.kyc.controllers;

import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.console.ApiKeyListItem;
import com.kyc.dto.console.AuditListResponse;
import com.kyc.dto.console.ChangePasswordRequest;
import com.kyc.dto.console.InviteRequest;
import com.kyc.dto.console.MeResponse;
import com.kyc.dto.console.PatchMemberRoleRequest;
import com.kyc.dto.console.TeamResponse;
import com.kyc.dto.console.TransferOwnershipRequest;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.CurrentConsole;
import com.kyc.services.AccountService;
import com.kyc.services.ConsoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console")
@Tag(name = "Console")
public class ConsoleController {

    private final ConsoleService consoleService;
    private final AccountService accountService;

    public ConsoleController(ConsoleService consoleService, AccountService accountService) {
        this.consoleService = consoleService;
        this.accountService = accountService;
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
    @Operation(summary = "Révoquer une clé")
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
        consoleService.invite(CurrentConsole.require(), body.email(), body.role());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/team/invites/{id}/resend")
    @Operation(summary = "Renvoyer une invitation")
    public ResponseEntity<Void> resendInvite(@PathVariable UUID id) {
        consoleService.resendInvite(CurrentConsole.require(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/team/invites/{id}")
    @Operation(summary = "Annuler une invitation")
    public ResponseEntity<Void> cancelInvite(@PathVariable UUID id) {
        consoleService.cancelInvite(CurrentConsole.require(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/team/members/{userId}")
    @Operation(summary = "Retirer un membre")
    public ResponseEntity<Void> removeMember(@PathVariable UUID userId) {
        consoleService.removeMember(CurrentConsole.require(), userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/team/members/{userId}")
    @Operation(summary = "Changer le rôle d’un membre")
    public ResponseEntity<Void> changeRole(
            @PathVariable UUID userId, @Valid @RequestBody PatchMemberRoleRequest body) {
        consoleService.changeRole(CurrentConsole.require(), userId, body.role());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/team/transfer")
    @Operation(summary = "Transférer la propriété du compte")
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferOwnershipRequest body) {
        consoleService.transfer(CurrentConsole.require(), body.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/team/members/{userId}/disable")
    @Operation(summary = "Désactiver un membre")
    public ResponseEntity<Void> disableMember(@PathVariable UUID userId) {
        consoleService.disableMember(CurrentConsole.require(), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/team/members/{userId}/enable")
    @Operation(summary = "Réactiver un membre")
    public ResponseEntity<Void> enableMember(@PathVariable UUID userId) {
        consoleService.enableMember(CurrentConsole.require(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit")
    @Operation(summary = "Journal d’activité de l’organisation")
    public AuditListResponse audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return consoleService.audit(CurrentConsole.require(), action, cursor, limit);
    }
}
