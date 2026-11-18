package com.kyc.authenticity;

/** Boîte COCO en pixels de la photo de référence. */
public record SourceBox(String name, String role, int x, int y, int width, int height) {}
