package com.kyc.controllers;

import com.kyc.dto.ConsentRequest;
import com.kyc.dto.ConsentResponse;
import com.kyc.dto.FlowSessionResponse;
import com.kyc.services.HostedFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/flow")
@Tag(name = "Hosted flow")
public class FlowController {

    private final HostedFlowService hostedFlowService;

    public FlowController(HostedFlowService hostedFlowService) {
        this.hostedFlowService = hostedFlowService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Hydrater le hosted flow (auth = possession du token)")
    public ResponseEntity<FlowSessionResponse> get(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(hostedFlowService.open(token));
    }

    @PostMapping("/{token}/consent")
    @Operation(summary = "Enregistrer le consentement applicant")
    public ResponseEntity<ConsentResponse> consent(
            @PathVariable String token,
            @Valid @RequestBody ConsentRequest body,
            HttpServletRequest request) {
        String ip = clientIp(request);
        String userAgent = request.getHeader("User-Agent");
        ConsentResponse response = hostedFlowService.consent(token, body.decision(), ip, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
