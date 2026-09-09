package com.kyc.controllers;

import com.kyc.dto.account.AcceptInviteRequest;
import com.kyc.dto.account.EmailRequest;
import com.kyc.dto.account.InvitePreviewResponse;
import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.account.LoginRequest;
import com.kyc.dto.account.PasswordResetRequest;
import com.kyc.dto.account.SignupRequest;
import com.kyc.dto.account.SignupResponse;
import com.kyc.services.AccountService;
import com.kyc.services.AuthRateLimiter;
import com.kyc.services.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/account")
@Tag(name = "Account")
public class AccountController {

    private final AccountService accountService;
    private final SessionService sessionService;
    private final AuthRateLimiter authRateLimiter;

    public AccountController(
            AccountService accountService, SessionService sessionService, AuthRateLimiter authRateLimiter) {
        this.accountService = accountService;
        this.sessionService = sessionService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/signup")
    @Operation(summary = "Créer un compte et une organisation Sandbox")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest body) {
        SignupResponse created = accountService.signup(
                body.email(),
                body.password(),
                body.organizationName(),
                body.inviteToken(),
                body.firstName(),
                body.lastName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/verify")
    @Operation(summary = "Vérifier l’e-mail et émettre la première clé ky_test_ (une fois)")
    public ResponseEntity<IssuedApiKeyResponse> verify(@RequestParam String token) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.verify(token));
    }

    @PostMapping("/verify/resend")
    @Operation(summary = "Renvoyer l’e-mail de vérification")
    public ResponseEntity<Void> resend(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        authRateLimiter.check("resend", clientIp(request));
        accountService.resend(body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @Operation(summary = "Ouvrir une session console")
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        authRateLimiter.check("login", clientIp(request));
        String session = accountService.login(body.email(), body.password());
        SessionService.write(response, sessionService.cookie(session));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "Fermer la session console")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        accountService.logout(sessionCookie(request));
        SessionService.write(response, sessionService.expiredCookie());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Demander une réinitialisation de mot de passe")
    public ResponseEntity<Void> forgot(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        authRateLimiter.check("forgot", clientIp(request));
        accountService.forgot(body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Réinitialiser le mot de passe")
    public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetRequest body) {
        accountService.reset(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invites")
    @Operation(summary = "Vérifier qu’un jeton d’invitation est encore valide")
    public InvitePreviewResponse peekInvite(@RequestParam String token) {
        return accountService.peekInvite(token);
    }

    @PostMapping("/invites/accept")
    @Operation(summary = "Accepter une invitation équipe")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest body) {
        accountService.acceptInvite(body.token());
        return ResponseEntity.noContent().build();
    }

    private static String sessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SessionService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma < 0 ? forwarded.trim() : forwarded.substring(0, comma).trim();
        }
        return request.getRemoteAddr();
    }
}
