package com.kyc.controllers;

import com.kyc.dto.antispoof.AntiSpoofLabResponse;
import com.kyc.security.CurrentApiKey;
import com.kyc.services.AntiSpoofLabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/anti-spoof")
@Tag(name = "Anti-spoof")
@SecurityRequirement(name = "bearer-api-key")
public class AntiSpoofController {

    private final AntiSpoofLabService lab;

    public AntiSpoofController(AntiSpoofLabService lab) {
        this.lab = lab;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Banc anti-spoof : assessment du service, sans créer de vérification")
    public ResponseEntity<AntiSpoofLabResponse> analyze(
            @Parameter(description = "JPEG d'origine") @RequestPart(value = "file", required = false)
                    MultipartFile file) {
        return lab.analyze(CurrentApiKey.require(), file);
    }
}
