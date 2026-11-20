package com.kyc.authenticity;

import java.awt.image.BufferedImage;

/** Image au canevas du gabarit. {@code detector} est {@code orb}, {@code sift}, ou vide pour la voie des coins. */
public record Rectified(String method, String detector, BufferedImage image) {}
