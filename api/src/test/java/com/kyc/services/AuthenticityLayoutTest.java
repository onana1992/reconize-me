package com.kyc.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kyc.authenticity.AuthenticityVerdict;
import com.kyc.dto.authenticity.FixedBlock;
import com.kyc.dto.authenticity.PositionBlock;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthenticityLayoutTest {

    @Test
    void aValueFieldCountsLikeARegion() {
        List<AuthenticityVerdict.Reading> readings = AuthenticityLabService.readings(
                List.of(new FixedBlock("flag", "fixed", 0.9, 0, "conforme", null)),
                List.of(
                        new PositionBlock("place", "value", 0.08, 40, "suspect", null),
                        new PositionBlock("barcode", "region", 0.8, 0, "conforme", null)));

        assertEquals("conforme", AuthenticityVerdict.decide(readings).verdict());
        assertEquals(3, readings.size());
    }

    @Test
    void aSuspectRegionStillFailsTheDocument() {
        List<AuthenticityVerdict.Reading> readings = AuthenticityLabService.readings(
                List.of(),
                List.of(
                        new PositionBlock("name", "value", 0.9, 0, "conforme", null),
                        new PositionBlock("barcode", "region", 0.1, 40, "suspect", null)));

        assertEquals("suspect", AuthenticityVerdict.decide(readings).verdict());
    }
}
