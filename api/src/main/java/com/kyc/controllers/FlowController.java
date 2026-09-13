package com.kyc.controllers;

import com.kyc.dto.idv.CompleteCaptureResponse;
import com.kyc.dto.idv.CompleteUploadRequest;
import com.kyc.dto.idv.ConsentRequest;
import com.kyc.dto.idv.ConsentResponse;
import com.kyc.dto.idv.FlowSessionResponse;
import com.kyc.dto.idv.UploadResponse;
import com.kyc.services.HostedFlowService;
import com.kyc.web.ClientIps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/flow/{token}")
@Tag(name = "Hosted flow")
public class FlowController {

    private final HostedFlowService flow;

    public FlowController(HostedFlowService flow) {
        this.flow = flow;
    }

    @GetMapping
    @Operation(summary = "Hydrater la session applicant")
    public FlowSessionResponse get(@PathVariable String token, HttpServletResponse response) {
        noStore(response);
        return flow.hydrate(token);
    }

    @PostMapping("/consent")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer le consentement")
    public ConsentResponse consent(
            @PathVariable String token,
            @RequestBody(required = false) ConsentRequest body,
            @RequestParam(value = "decision", required = false) String decision,
            HttpServletRequest request,
            HttpServletResponse response) {
        noStore(response);
        String resolved = body != null && body.decision() != null && !body.decision().isBlank() ? body.decision() : decision;
        return flow.consent(token, new ConsentRequest(resolved), ClientIps.from(request), request.getHeader("User-Agent"));
    }

    @PostMapping("/document/uploads")
    @Operation(summary = "URL signée pour la pièce")
    public UploadResponse documentUploads(@PathVariable String token, HttpServletResponse response) {
        noStore(response);
        return flow.documentUpload(token);
    }

    @PostMapping("/document/complete")
    @Operation(summary = "Valider l’upload de la pièce")
    public CompleteCaptureResponse documentComplete(
            @PathVariable String token, @RequestBody(required = false) CompleteUploadRequest body, HttpServletResponse response) {
        noStore(response);
        return flow.documentComplete(token, body);
    }

    @PostMapping("/selfie/uploads")
    @Operation(summary = "URL signée pour le selfie")
    public UploadResponse selfieUploads(@PathVariable String token, HttpServletResponse response) {
        noStore(response);
        return flow.selfieUpload(token);
    }

    @PostMapping("/selfie/complete")
    @Operation(summary = "Valider l’upload du selfie")
    public CompleteCaptureResponse selfieComplete(
            @PathVariable String token, @RequestBody(required = false) CompleteUploadRequest body, HttpServletResponse response) {
        noStore(response);
        return flow.selfieComplete(token, body);
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
}
