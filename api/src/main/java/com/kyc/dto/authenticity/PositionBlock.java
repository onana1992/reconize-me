package com.kyc.dto.authenticity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Position d'un champ ou d'une région sur le canevas.")
public record PositionBlock(
        @Schema(description = "Nom de catégorie") String name,
        @Schema(description = "value pour un champ, region pour une zone", allowableValues = {"value", "region"})
                String role,
        @Schema(description = "Intersection sur union avec la boîte projetée") double iou,
        @Schema(description = "Plus grand écart de bord, en pixels du canevas") int deltaPx,
        @Schema(description = "conforme, a_verifier ou suspect", allowableValues = {"conforme", "a_verifier", "suspect"})
                String status,
        @Schema(description = "field_absent, zone_unmeasured, ou absent si la position est mesurée") String code) {}
