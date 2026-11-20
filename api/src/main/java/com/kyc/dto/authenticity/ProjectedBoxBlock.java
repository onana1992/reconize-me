package com.kyc.dto.authenticity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Boîte COCO ramenée au canevas du gabarit.")
public record ProjectedBoxBlock(
        @Schema(description = "Nom de catégorie") String name,
        @Schema(description = "fixed, value ou region", allowableValues = {"fixed", "value", "region"}) String role,
        @Schema(description = "x, y, largeur, hauteur, en pixels du canevas") int[] rectangle) {}
