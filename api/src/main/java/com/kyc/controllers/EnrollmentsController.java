package com.kyc.controllers;

import com.kyc.dto.auth.EnrollmentResponse;
import com.kyc.enums.ProductCode;
import com.kyc.security.ApiPrincipal;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.EnrollmentService;
import com.kyc.services.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/enrollments")
@Tag(name = "Enrollments")
public class EnrollmentsController {

    private final EnrollmentService enrollments;
    private final IntegrationService integrations;

    public EnrollmentsController(EnrollmentService enrollments, IntegrationService integrations) {
        this.enrollments = enrollments;
        this.integrations = integrations;
    }

    @PostMapping
    @Operation(summary = "Manual enrollment is refused")
    public EnrollmentResponse create() {
        requireAuthProduct();
        enrollments.rejectManualCreate();
        return EnrollmentResponse.absent();
    }

    @GetMapping
    @Operation(summary = "Lookup enrollment by external_id")
    public EnrollmentResponse lookup(@RequestParam(name = "external_id") String externalId) {
        ApiPrincipal principal = requireAuthProduct();
        return enrollments.lookup(principal.organizationId(), externalId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one enrollment")
    public EnrollmentResponse get(@PathVariable UUID id) {
        ApiPrincipal principal = requireAuthProduct();
        return enrollments.get(principal.organizationId(), id);
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke an active enrollment")
    public EnrollmentResponse revoke(@PathVariable UUID id) {
        ApiPrincipal principal = requireAuthProduct();
        return enrollments.revoke(principal.organizationId(), id);
    }

    private ApiPrincipal requireAuthProduct() {
        ApiPrincipal principal = CurrentApiKey.require();
        integrations.requireProduct(
                principal.organizationId(), principal.integrationId(), ProductCode.AUTHENTICATION);
        return principal;
    }
}
