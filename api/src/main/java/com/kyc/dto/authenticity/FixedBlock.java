package com.kyc.dto.authenticity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ressemblance d'un élément fixe ou d'un libellé sur le canevas.")
public record FixedBlock(
        @Schema(description = "Nom de catégorie") String name,
        @Schema(description = "fixed pour une marque, value pour un libellé", allowableValues = {"fixed", "value"})
                String role,
        @Schema(description = "NCC, 1 pour une copie fidèle") double score,
        @Schema(description = "Décalage du meilleur endroit, en pixels du canevas") int shiftPx,
        @Schema(description = "conforme, a_verifier ou suspect", allowableValues = {"conforme", "a_verifier", "suspect"})
                String status,
        @Schema(description = "Code de l'élément, absent tant qu'aucune règle n'en pose") String code) {}
