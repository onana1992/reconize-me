package com.kyc.controllers;

import com.kyc.dto.billing.UsageResponse;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/usage")
@Tag(name = "Usage")
public class UsageController {

    private final CreditService credits;

    public UsageController(CreditService credits) {
        this.credits = credits;
    }

    @GetMapping
    @Operation(summary = "Solde et consommation de l’organisation (Bearer)")
    public UsageResponse get() {
        return credits.usage(CurrentApiKey.require().organizationId());
    }
}
