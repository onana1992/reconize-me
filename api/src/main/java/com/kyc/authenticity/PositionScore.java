package com.kyc.authenticity;

/** Position d'un champ ou d'une région, mesurée sur le canevas. */
public record PositionScore(String name, String role, double iou, int deltaPx, String status, String code) {}
