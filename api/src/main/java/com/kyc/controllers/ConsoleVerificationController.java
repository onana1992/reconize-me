package com.kyc.controllers;

import com.kyc.security.ConsoleAuth;
import com.kyc.security.CurrentConsole;
import com.kyc.security.Permission;
import com.kyc.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/verifications")
@Tag(name = "Console")
public class ConsoleVerificationController {

    @GetMapping
    @Operation(summary = "Liste des vérifications (droits T2 ; données M4)")
    public List<Object> list() {
        ConsoleAuth.require(CurrentConsole.require(), Permission.VERIFICATION_READ);
        return List.of();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche vérification (droits T2 ; données M4)")
    public void get(@PathVariable UUID id) {
        ConsoleAuth.require(CurrentConsole.require(), Permission.VERIFICATION_READ);
        throw ApiException.notFound("Verification not found");
    }

    @PostMapping
    @Operation(summary = "Créer une vérification (droits T2 ; métier M4)")
    public void create() {
        ConsoleAuth.require(CurrentConsole.require(), Permission.VERIFICATION_WRITE);
        throw ApiException.gone("idv_unavailable", "Identity verification is not available yet");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une vérification (droits T2 ; métier M4)")
    public void cancel(@PathVariable UUID id) {
        ConsoleAuth.require(CurrentConsole.require(), Permission.VERIFICATION_WRITE);
        throw ApiException.notFound("Verification not found");
    }
}
