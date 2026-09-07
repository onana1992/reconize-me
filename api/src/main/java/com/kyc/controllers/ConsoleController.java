package com.kyc.controllers;

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
import org.springframework.web.bind.annotation.RequestMapping;
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
