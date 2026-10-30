package com.kyc.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class VisionFrameTest {

    @Test
    void undecodableBytesAreLeftUntouched() {
        byte[] image = {1, 2, 3, 4};

        assertSame(image, VisionFrame.fit(image));
    }

    @Test
    void photoAlreadyWithinTheLongSideIsLeftUntouched() throws IOException {
        byte[] image = jpeg(800, 600, 0x336699);

        assertSame(image, VisionFrame.fit(image));
    }

    @Test
    void oversizedPhotoIsReducedToTheLongSideTheModelActuallyReads() throws IOException {
        byte[] fitted = VisionFrame.fit(jpeg(3840, 2160, 0x224466));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(fitted));
        assertEquals(VisionFrame.MAX_LONG_SIDE, Math.max(image.getWidth(), image.getHeight()));
        assertTrue(fitted.length < 700_000);
        assertEquals(1, VisionFrame.orientation(fitted));
    }

    @Test
    void phoneOrientationIsAppliedBeforeTheResize() {
        BufferedImage source = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xFF0000);
        source.setRGB(3, 1, 0x0000FF);

        BufferedImage turned = VisionFrame.orient(source, 6);

        assertEquals(2, turned.getWidth());
        assertEquals(4, turned.getHeight());
        assertEquals(0xFF0000, turned.getRGB(1, 0) & 0xFFFFFF);
        assertEquals(0x0000FF, turned.getRGB(0, 3) & 0xFFFFFF);
    }

    private static byte[] jpeg(int width, int height, int rgb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }
}
