package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DecodedSpecimensTest {

    @Test
    void dropsTheOldestPastEight() throws Exception {
        Path dir = Files.createTempDirectory("decoded-specimens");
        DecodedSpecimens decoded = new DecodedSpecimens();
        BufferedImage pixel = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 9; i++) {
            Path file = dir.resolve("p" + i + ".png");
            ImageIO.write(pixel, "png", file.toFile());
            decoded.load("folder-" + i, file);
        }

        assertEquals(DecodedSpecimens.LIMIT, decoded.size());
    }
}
