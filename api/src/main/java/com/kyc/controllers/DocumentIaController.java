package com.kyc.controllers;

import com.kyc.dto.documentia.DocumentIaAnalysisResponse;
import com.kyc.dto.documentia.DocumentIaCatalogResponse;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.DocumentIaLabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/document-ia")
@Tag(name = "Document IA")
@SecurityRequirement(name = "bearer-api-key")
public class DocumentIaController {

    private final DocumentIaLabService lab;

    public DocumentIaController(DocumentIaLabService lab) {
        this.lab = lab;
    }

    @GetMapping("/catalog")
    @Operation(summary = "Catalogue des schémas actifs envoyés au modèle")
    public DocumentIaCatalogResponse catalog() {
        CurrentApiKey.require();
        return lab.catalog();
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Banc Document IA : enveloppe d’analyse, sans créer de vérification")
    public DocumentIaAnalysisResponse analyze(
            @Parameter(description = "quality, parse, vision, normalize, mrz, validate ou decide.")
                    @RequestParam(required = false)
                    String until,
            @Parameter(description = "Image du document") @RequestPart(value = "file", required = false) MultipartFile file) {
        return lab.analyze(CurrentApiKey.require(), until, file);
    }

    @PostMapping("/fixtures/{name}")
    @Operation(summary = "Rejoue une fixture JSON, sans appel vision")
    public DocumentIaAnalysisResponse fixture(
            @PathVariable String name,
            @Parameter(description = "parse, normalize, mrz, validate ou decide.")
                    @RequestParam(required = false)
                    String until) {
        CurrentApiKey.require();
        return lab.fixture(name, until);
    }
}
