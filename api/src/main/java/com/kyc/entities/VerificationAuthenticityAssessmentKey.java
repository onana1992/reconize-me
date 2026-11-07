package com.kyc.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class VerificationAuthenticityAssessmentKey implements Serializable {

    private UUID verificationId;
    private String side;

    public VerificationAuthenticityAssessmentKey() {}

    public VerificationAuthenticityAssessmentKey(UUID verificationId, String side) {
        this.verificationId = verificationId;
        this.side = side;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VerificationAuthenticityAssessmentKey key)) {
            return false;
        }
        return Objects.equals(verificationId, key.verificationId) && Objects.equals(side, key.side);
    }

    @Override
    public int hashCode() {
        return Objects.hash(verificationId, side);
    }
}
