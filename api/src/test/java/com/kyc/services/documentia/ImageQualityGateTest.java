package com.kyc.services.documentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.dto.documentia.DocumentImageQuality;
import com.kyc.web.ApiException;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ImageQualityGateTest {

    private final ImageQualityGate gate = new ImageQualityGate();

    @Test
    void sharpFrameIsReadable() {
        DocumentImageQuality quality = gate.assess(sampledChecker(640, 480, 0x000000, 0xFFFFFF));
        assertTrue(quality.readable());
        assertFalse(quality.blur());
        assertFalse(quality.tooSmall());
        assertFalse(quality.lowLight());
        assertFalse(quality.overexposed());
        assertFalse(quality.glare());
        assertFalse(quality.unreadable());
        assertNull(quality.reason());
    }

    @Test
    void shortSideBelow480IsTooSmall() {
        DocumentImageQuality quality = gate.assess(flat(100, 80, 0x808080));
        assertFalse(quality.readable());
        assertTrue(quality.tooSmall());
        assertTrue(quality.unreadable());
        assertEquals("TOO_SMALL", quality.reason());
    }

    @Test
    void darkSharpFrameIsLowLight() {
        DocumentImageQuality quality = gate.assess(sampledChecker(640, 480, 0x000000, 0x121212));
        assertFalse(quality.readable());
        assertTrue(quality.lowLight());
        assertFalse(quality.blur());
        assertEquals("LOW_LIGHT", quality.reason());
    }

    @Test
    void brightSharpFrameIsOverexposed() {
        DocumentImageQuality quality = gate.assess(sampledChecker(640, 480, 0xFAFAFA, 0xFFFFFF));
        assertFalse(quality.readable());
        assertTrue(quality.overexposed());
        assertFalse(quality.blur());
        assertEquals("OVEREXPOSED", quality.reason());
    }

    @Test
    void flatFrameIsBlurry() {
        DocumentImageQuality quality = gate.assess(flat(640, 480, 0x808080));
        assertFalse(quality.readable());
        assertTrue(quality.blur());
        assertFalse(quality.tooSmall());
        assertEquals("IMAGE_TOO_BLURRY", quality.reason());
    }

    @Test
    void unreadableBytesAreRejected() {
        assertThrows(ApiException.class, () -> gate.assess(new byte[] {1, 2, 3, 4}));
    }

    private static BufferedImage flat(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static BufferedImage sampledChecker(int width, int height, int dark, int light) {
        int step = Math.max(1, (int) Math.round(Math.min(width, height) / 320.0));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int sampleWidth = Math.max(1, width / step);
        int sampleHeight = Math.max(1, height / step);
        for (int gy = 0; gy < sampleHeight; gy++) {
            for (int gx = 0; gx < sampleWidth; gx++) {
                image.setRGB(
                        Math.min(width - 1, gx * step),
                        Math.min(height - 1, gy * step),
                        ((gx + gy) % 2 == 0) ? dark : light);
            }
        }
        return image;
    }
}
