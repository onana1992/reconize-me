package com.kyc.controllers;

import com.kyc.dto.billing.BillingResponse;
import com.kyc.dto.billing.CheckoutResponse;
import com.kyc.dto.billing.CreateCheckoutRequest;
import com.kyc.security.CurrentConsole;
import com.kyc.services.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/billing")
@Tag(name = "Console")
public class ConsoleBillingController {

    private final CreditService credits;

    public ConsoleBillingController(CreditService credits) {
        this.credits = credits;
    }

    @GetMapping
    @Operation(summary = "Solde, ledger et consommation (owner)")
    public BillingResponse get(
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return credits.billing(CurrentConsole.require(), cursor, limit);
    }

    @PostMapping("/checkout")
    @Operation(summary = "Créer une session Stripe Checkout (carte)")
    public ResponseEntity<CheckoutResponse> checkout(@RequestBody(required = false) CreateCheckoutRequest body) {
        Long pack = body == null ? null : body.packMinor();
        return ResponseEntity.status(HttpStatus.CREATED).body(credits.createCheckout(CurrentConsole.require(), pack));
    }
}
