package com.kyc.controllers;

import com.kyc.dto.authenticity.AuthenticityCheckResponse;
import com.kyc.dto.authenticity.AuthenticityTemplatesResponse;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.AuthenticityLabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/authenticity")
@Tag(name = "Authenticité visuelle")
@SecurityRequirement(name = "bearer-api-key")
public class AuthenticityController {

    private final AuthenticityLabService lab;

    public AuthenticityController(AuthenticityLabService lab) {
        this.lab = lab;
    }

    @GetMapping("/templates")
    @Operation(summary = "Liste des gabarits chargés (référence + COCO)")
    public AuthenticityTemplatesResponse templates() {
        CurrentApiKey.require();
        return lab.templates();
    }

    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Banc authenticité : enveloppe de contrôle, sans créer de vérification")
    public AuthenticityCheckResponse check(
            @Parameter(description = "Triplet code:version:side du gabarit") @RequestParam String documentId,
            @Parameter(description = "rectify, project, fixed, positions ou decide.")
                    @RequestParam(required = false)
                    String until,
            @Parameter(description = "Image de la pièce") @RequestPart(value = "file", required = false)
                    MultipartFile file) {
        CurrentApiKey.require();
        return lab.check(documentId, until, file);
    }
}
