package com.kyc.controllers;

import com.kyc.entities.Verification;
import com.kyc.services.VerificationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/verifications")
public class VerificationsController {

    private final VerificationService verificationService;

    public VerificationsController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Verification> get(
            @PathVariable UUID id, @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        if (organizationId == null) {
            return ResponseEntity.notFound().build();
        }
        return verificationService
                .findForOrganization(id, organizationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
