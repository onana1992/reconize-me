package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixedMatcherTest {

    private final FixedMatcher matcher = new FixedMatcher(new BoxProjector());

    @Test
    void statusFollowsTheGlobalThresholds() {
        assertEquals("conforme", FixedMatcher.status(0.75));
        assertEquals("a_verifier", FixedMatcher.status(0.7499));
        assertEquals("a_verifier", FixedMatcher.status(0.50));
        assertEquals("suspect", FixedMatcher.status(0.4999));
    }

    @Test
    void identicalCanvasScoresHighAndAShiftStaysInsideTheMargin() {
        BufferedImage reference = plate(false, 0);
        BufferedImage shifted = plate(false, 4);

        List<FixedScore> scores = matcher.match(template(reference), shifted);

        FixedScore moved = scores.get(0);
        FixedScore still = scores.get(1);
        assertEquals("conforme", moved.status());
        assertTrue(moved.score() >= 0.75);
        assertEquals(4, moved.shiftPx());
        assertEquals("conforme", still.status());
        assertEquals(0, still.shiftPx());
    }

    @Test
    void aHiddenMarkFallsBelowTheSuspectThreshold() {
        BufferedImage reference = plate(false, 0);
        BufferedImage masked = plate(true, 0);

        List<FixedScore> scores = matcher.match(template(reference), masked);

        assertEquals("mark", scores.get(0).name());
        assertTrue(scores.get(0).score() < 0.50);
        assertEquals("suspect", scores.get(0).status());
        assertEquals("conforme", scores.get(1).status());
        assertTrue(scores.get(1).score() >= 0.75);
    }

    private static AuthenticityTemplate template(BufferedImage reference) {
        return new AuthenticityTemplate(
                "SAMPLE_CARD:2019:FRONT",
                "SAMPLE_CARD",
                "2019",
                "FRONT",
                80,
                40,
                0,
                0,
                80,
                40,
                80,
                40,
                2,
                2,
                0,
                List.of(),
                List.of(
                        new SourceBox("mark", "fixed", 8, 8, 18, 14),
                        new SourceBox("seal", "fixed", 46, 8, 18, 14)),
                List.of(),
                reference);
    }

    private static BufferedImage plate(boolean maskFirst, int shift) {
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 80; x++) {
                image.setRGB(x, y, 0x808080);
            }
        }
        if (!maskFirst) {
            stripes(image, 8 + shift, 8, 18, 14, 3);
        }
        stripes(image, 46, 8, 18, 14, 2);
        return image;
    }

    private static void stripes(BufferedImage image, int x, int y, int width, int height, int period) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int value = (column / period) % 2 == 0 ? 20 : 230;
                int pixel = (value << 16) | (value << 8) | value;
                image.setRGB(x + column, y + row, pixel);
            }
        }
    }
}
