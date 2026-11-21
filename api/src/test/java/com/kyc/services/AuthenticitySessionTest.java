package com.kyc.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.authenticity.SpecimenCache;
import com.kyc.authenticity.TemplateRegistry;
import com.kyc.dto.documentia.ParsedDocument.Classification;
import com.kyc.enums.SignalOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.services.AuthenticitySession.Kind;
import com.kyc.services.IdvDecisionEngine.Result;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AuthenticitySessionTest {

    private final AuthenticitySession session = new AuthenticitySession(true, new TemplateRegistry(), lab());

    @Test
    void theReferenceMatchesTheBench() throws Exception {
        byte[] image;
        image = SpecimenCache.shipped("QUEBEC_DRIVER_LICENSE_2024_FRONT");

        AuthenticitySession.Outcome outcome = session.assess(quebec(), "FRONT", image);

        assertEquals(Kind.ACCEPTED, outcome.kind());
        assertEquals("conforme", outcome.verdict());
        assertEquals("QUEBEC_DRIVER_LICENSE:2024:FRONT", outcome.documentId());
        assertNotNull(outcome.score());
        assertTrue(outcome.zones() > 0);
        assertTrue(outcome.score() > 0.5);
    }

    @Test
    void aClassWithoutATemplateCannotBeApproved() {
        Classification passport = new Classification("PASSPORT_TD3", "CA", "CA", "PASSPORT", "2024", "FRONT", 0.9);

        AuthenticitySession.Outcome outcome = session.assess(passport, "FRONT", jpeg());

        assertEquals(Kind.UNSUPPORTED, outcome.kind());
        Result folded = session.apply(approved(), outcome.verdict());
        assertEquals(VerificationDecision.REVIEW, folded.decision());
        assertEquals("authenticity_unsupported", folded.reasons().get(0));
        assertEquals(SignalOutcome.UNAVAILABLE, folded.signals().get(0).outcome());
    }

    @Test
    void aFlatImageAsksForAnotherCapture() {
        AuthenticitySession.Outcome outcome = session.assess(quebec(), "FRONT", jpeg());

        assertEquals(Kind.RECAPTURE, outcome.kind());
        assertEquals("alignment_failed", outcome.recaptureReason());
    }

    @Test
    void aSuspectDocumentIsDeclinedAndBlocksReview() {
        Result folded = session.apply(approved(), "suspect");

        assertEquals(VerificationDecision.DECLINED, folded.decision());
        assertEquals("authenticity_failed", folded.reasons().get(0));
        assertEquals(SignalOutcome.FAIL, folded.signals().get(folded.signals().size() - 1).outcome());
    }

    @Test
    void aBackWithoutATemplateIsSkipped() {
        Classification passport = new Classification("PASSPORT_TD3", "CA", "CA", "PASSPORT", "2024", "BACK", 0.9);

        assertEquals(Kind.SKIPPED, session.assess(passport, "BACK", jpeg(), false).kind());
    }

    @Test
    void aMeasuredBackKeepsTheWorseVerdict() {
        assertEquals("suspect", AuthenticitySession.combine("conforme", "suspect"));
        assertEquals("conforme", AuthenticitySession.combine("conforme", "conforme"));
        assertEquals(
                "QUEBEC_DRIVER_LICENSE:2024:FRONT|QUEBEC_DRIVER_LICENSE:2024:BACK",
                AuthenticitySession.documents(
                        "QUEBEC_DRIVER_LICENSE:2024:FRONT", "QUEBEC_DRIVER_LICENSE:2024:BACK"));
    }

    @Test
    void theFlagOffLeavesTheDocumentAlone() {
        AuthenticitySession quiet = new AuthenticitySession(false, new TemplateRegistry(), lab());

        assertEquals(Kind.SKIPPED, quiet.assess(quebec(), "FRONT", jpeg()).kind());
        assertEquals(VerificationDecision.APPROVED, quiet.apply(approved(), null).decision());
    }

    private static AuthenticityLabService lab() {
        return new AuthenticityLabService(
                null,
                new TemplateRegistry(),
                new com.kyc.authenticity.CardRectifier(),
                new com.kyc.authenticity.BoxProjector(),
                new com.kyc.authenticity.FixedMatcher(new com.kyc.authenticity.BoxProjector()),
                new com.kyc.authenticity.PositionMeasurer(new com.kyc.authenticity.BoxProjector()));
    }

    private static Classification quebec() {
        return new Classification("QUEBEC_DRIVER_LICENSE", "CA", "QC", "DRIVER_LICENSE", "2024", "FRONT", 0.99);
    }

    private static Result approved() {
        return new Result(VerificationDecision.APPROVED, List.of("vision_pass"), List.of(), Map.of("document_code", "X"));
    }

    private static byte[] jpeg() {
        BufferedImage image = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(180, 180, 180));
        graphics.fillRect(0, 0, 640, 400);
        graphics.dispose();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", bytes);
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
