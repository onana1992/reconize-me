package com.kyc.dto.documentia;

/** Scores recalculés en Java. overallScore n'est pas la confiance brute du modèle. */
public record DocumentScores(
        double documentTypeScore,
        double qualityScore,
        double ocrScore,
        double fieldScore,
        Double mrzScore,
        double validationScore,
        double overallScore,
        String band) {}
