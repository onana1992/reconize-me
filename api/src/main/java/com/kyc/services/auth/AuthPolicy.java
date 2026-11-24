package com.kyc.services.auth;

import com.kyc.enums.AuthOnBorderline;
import java.math.BigDecimal;

/** Factory policy auth-policy-0. Applied when the organization has not published a version. */
public record AuthPolicy(
        String version,
        BigDecimal approvalThreshold,
        BigDecimal reviewThreshold,
        AuthOnBorderline onBorderline,
        int maxCaptureAttempts,
        boolean livenessRequired) {

    public static final BigDecimal FLOOR_APPROVAL = new BigDecimal("0.900");
    public static final BigDecimal CEILING_APPROVAL = new BigDecimal("1.000");
    public static final BigDecimal FLOOR_REVIEW = new BigDecimal("0.800");
    public static final BigDecimal LIVENESS_PASS = new BigDecimal("0.800");

    public static AuthPolicy factory() {
        return new AuthPolicy(
                "auth-policy-0",
                FLOOR_APPROVAL,
                FLOOR_REVIEW,
                AuthOnBorderline.DECLINE,
                3,
                true);
    }
}
