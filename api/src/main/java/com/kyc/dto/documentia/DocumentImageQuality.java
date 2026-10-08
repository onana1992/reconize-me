package com.kyc.dto.documentia;

/**
 * Mesures serveur du banc. Les flags que seul le modèle voit restent faux jusqu'au palier vision.
 */
public record DocumentImageQuality(
        boolean readable,
        boolean blur,
        boolean glare,
        boolean cropped,
        boolean partiallyVisible,
        boolean multipleDocuments,
        boolean tooSmall,
        boolean lowLight,
        boolean overexposed,
        boolean tilted,
        boolean unreadable,
        String reason) {}
