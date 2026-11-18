package com.kyc.authenticity;

/** Score d'un élément fixe ou d'un libellé, mesuré sur le canevas. */
public record FixedScore(String name, String role, double score, int shiftPx, String status) {}
