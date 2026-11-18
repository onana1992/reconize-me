package com.kyc.dto.authenticity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Carte ramenée au canevas dérivé du rectangle document du COCO.")
public record RectifyBlock(
        @Schema(description = "corners ou features", allowableValues = {"corners", "features"}) String method,
        @Schema(description = "Largeur puis hauteur du canevas, en pixels") int[] canvas) {}
