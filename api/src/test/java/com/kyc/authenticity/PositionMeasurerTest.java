package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionMeasurerTest {

    private final PositionMeasurer measurer = new PositionMeasurer(new BoxProjector());

    @Test
    void statusFollowsTheGlobalThresholds() {
        assertEquals("conforme", PositionMeasurer.status(0.50, 34));
        assertEquals("a_verifier", PositionMeasurer.status(0.49, 34));
        assertEquals("a_verifier", PositionMeasurer.status(0.20, 0));
        assertEquals("suspect", PositionMeasurer.status(0.19, 0));
        assertEquals("suspect", PositionMeasurer.status(1, 35));
    }

    @Test
    void identicalCanvasIsConforme() {
        BufferedImage reference = plate(0, false);

        PositionScore score = measurer.measure(template(reference), reference).get(0);

        assertEquals("conforme", score.status());
        assertNull(score.code());
        assertEquals(0, score.deltaPx());
        assertTrue(score.iou() >= 0.50);
    }

    @Test
    void aShiftOfThirtyFivePixelsIsSuspect() {
        BufferedImage reference = plate(0, false);
        BufferedImage shifted = plate(TemplateEngine.MAX_SHIFT_PX, false);

        PositionScore score = measurer.measure(template(reference), shifted).get(0);

        assertEquals("nom", score.name());
        assertEquals("suspect", score.status(), score.toString());
        assertNull(score.code(), score.toString());
        assertTrue(score.deltaPx() >= TemplateEngine.MAX_SHIFT_PX, score.toString());
    }

    @Test
    void differentInkInTheSameBoxIsConforme() {
        BufferedImage reference = plate(0, false);
        BufferedImage other = paper();
        for (int y = 22; y < 36; y++) {
            for (int x = 22; x < 54; x++) {
                int value = (x + y) % 2 == 0 ? 12 : 48;
                other.setRGB(x, y, (value << 16) | (value << 8) | value);
            }
        }

        PositionScore score = measurer.measure(template(reference), other).get(0);

        assertEquals("conforme", score.status(), score.toString());
        assertNull(score.code(), score.toString());
        assertTrue(score.iou() >= 0.50, score.toString());
        assertTrue(score.deltaPx() < TemplateEngine.MAX_SHIFT_PX, score.toString());
    }

    @Test
    void missingContentIsFieldAbsent() {
        BufferedImage reference = plate(0, false);

        PositionScore score = measurer.measure(template(reference), paper()).get(0);

        assertEquals("suspect", score.status(), score.toString());
        assertEquals("field_absent", score.code(), score.toString());
    }

    @Test
    void referenceDocumentInkSitsInTheAnnotatedBoxes() throws Exception {
        AuthenticityTemplate template = quebec();
        BufferedImage document = template.reference().getSubimage(
                template.documentX(), template.documentY(), template.documentWidth(), template.documentHeight());

        StringBuilder failures = new StringBuilder();
        for (PositionScore score : measurer.measure(template, document)) {
            if (!"conforme".equals(score.status()) || score.iou() < 0.50 || score.deltaPx() >= 35) {
                failures.append(score).append('\n');
            }
        }
        assertEquals("", failures.toString());
    }

    @Test
    void aBurnedWindowIsUnmeasured() {
        BufferedImage reference = plate(0, false);
        BufferedImage burned = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 80; y++) {
            for (int x = 0; x < 120; x++) {
                burned.setRGB(x, y, 0xF0F0F0);
            }
        }

        PositionScore score = measurer.measure(template(reference), burned).get(0);

        assertEquals("a_verifier", score.status());
        assertEquals("zone_unmeasured", score.code());
    }

    private static AuthenticityTemplate quebec() throws Exception {
        byte[] image;
        byte[] coco;
        try (InputStream reference = PositionMeasurerTest.class.getResourceAsStream(
                        "/authenticity/QUEBEC_DRIVER_LICENSE_2024_FRONT/reference.jpg");
                InputStream annotations = PositionMeasurerTest.class.getResourceAsStream(
                        "/authenticity/QUEBEC_DRIVER_LICENSE_2024_FRONT/annotations.coco.json")) {
            image = reference.readAllBytes();
            coco = annotations.readAllBytes();
        }
        return TemplateRegistry.load("QUEBEC_DRIVER_LICENSE_2024_FRONT", image, coco);
    }

    private static AuthenticityTemplate template(BufferedImage reference) {
        return new AuthenticityTemplate(
                "SAMPLE_CARD:2019:FRONT",
                "SAMPLE_CARD",
                "2019",
                "FRONT",
                120,
                80,
                0,
                0,
                120,
                80,
                120,
                80,
                1.5,
                0,
                1,
                List.of(),
                List.of(),
                List.of(new SourceBox("nom", "value", 20, 20, 36, 18)),
                reference);
    }

    private static BufferedImage plate(int shift, boolean absent) {
        BufferedImage image = paper();
        if (!absent) {
            for (int y = 20; y < 38; y++) {
                for (int x = 0; x < 36; x++) {
                    int value = 20 + (x + y) % 9;
                    image.setRGB(20 + shift + x, y, (value << 16) | (value << 8) | value);
                }
            }
        }
        return image;
    }

    private static BufferedImage paper() {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 80; y++) {
            for (int x = 0; x < 120; x++) {
                int value = 200 + ((x * 3 + y * 5) % 17);
                image.setRGB(x, y, (value << 16) | (value << 8) | value);
            }
        }
        return image;
    }
}
