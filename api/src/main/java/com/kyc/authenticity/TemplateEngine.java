package com.kyc.authenticity;

/**
 * Constantes uniques du moteur {@code template-1}. Elles valent pour tous les gabarits.
 * Les seuils de ressemblance et de position sont ceux du cahier, section 6.3.
 */
public final class TemplateEngine {

    public static final int CANVAS_SCALE = 1;
    public static final double ASPECT_TOLERANCE = 0.01;
    public static final double MIN_CARD_AREA_RATIO = 0.08;
    public static final int DETECT_LONG_SIDE = 1600;
    public static final double LOWE_RATIO = 0.75;
    public static final int MIN_INLIERS = 12;
    public static final double MIN_INLIER_RATIO = 0.40;
    public static final double RANSAC_REPROJ_PX = 3;
    public static final double QUAD_OVERFLOW = 0.05;
    public static final int RNG_SEED = 1;

    public static final String SIMILARITY = "NCC";
    public static final double SIMILARITY_PASS = 0.75;
    public static final double SIMILARITY_SUSPECT = 0.50;
    public static final int SEARCH_MARGIN_PX = 15;
    public static final double IOU_PASS = 0.50;
    public static final double IOU_SUSPECT = 0.20;
    public static final int MAX_SHIFT_PX = 35;

    private TemplateEngine() {}
}
