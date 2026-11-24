package com.kyc.controllers;

import com.kyc.dto.auth.EnrollmentResponse;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.CurrentConsole;
import com.kyc.security.Permission;
import com.kyc.services.EnrollmentService;
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
@RequestMapping("/v1/console/enrollments")
@Tag(name = "Console")
public class ConsoleEnrollmentsController {

    private final EnrollmentService enrollments;

    public ConsoleEnrollmentsController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @GetMapping
    @Operation(summary = "Enrôlements de l’organisation")
    public List<EnrollmentResponse> list() {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_READ);
        return enrollments.list(principal.organizationId());
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Révoquer une fiche active")
    public EnrollmentResponse revoke(@PathVariable UUID id) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.VERIFICATION_WRITE);
        return enrollments.revoke(principal.organizationId(), id);
    }
}
