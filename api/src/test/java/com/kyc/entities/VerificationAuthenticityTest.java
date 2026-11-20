package com.kyc.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VerificationAuthenticityTest {

    @Test
    void conformeFreezesTheVersionAndASuspectBlocksApproval() {
        Verification verification = new Verification();
        verification.recordAuthenticity("conforme", "QUEBEC_DRIVER_LICENSE:2024:FRONT", 0.90, 2);
        verification.recordAuthenticity("conforme", "QUEBEC_DRIVER_LICENSE:2024:BACK", 0.80, 2);

        assertEquals("template-1", verification.getAuthenticityVersion());
        assertEquals("QUEBEC_DRIVER_LICENSE:2024:BACK", verification.getAuthenticityDocumentId());
        assertEquals(0.85, verification.getAuthenticityScore(), 0.0001);
        assertFalse(verification.authenticityBlocksApproval());

        verification.recordAuthenticity("suspect", "QUEBEC_DRIVER_LICENSE:2024:FRONT", null, 0);

        assertNull(verification.getAuthenticityVersion());
        assertEquals(0.85, verification.getAuthenticityScore(), 0.0001);
        assertTrue(verification.authenticityBlocksApproval());
    }
}
