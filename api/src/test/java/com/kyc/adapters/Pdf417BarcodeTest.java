package com.kyc.adapters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.pdf417.PDF417Writer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class Pdf417BarcodeTest {

    @Test
    void jpegOfAPdf417IsPresent() throws Exception {
        assertTrue(Pdf417Barcode.present(jpeg(cardWithBarcode())));
    }

    @Test
    void blankCardIsAbsent() throws Exception {
        BufferedImage card = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = card.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, card.getWidth(), card.getHeight());
        graphics.dispose();
        assertFalse(Pdf417Barcode.present(jpeg(card)));
    }

    @Test
    void undecodableBytesAreAbsent() {
        assertFalse(Pdf417Barcode.present(new byte[] {1, 2, 3, 4}));
    }

    private static BufferedImage cardWithBarcode() throws WriterException {
        BitMatrix matrix = new PDF417Writer().encode("SAAQ-2024", BarcodeFormat.PDF_417, 300, 100);
        int scale = 2;
        BufferedImage card = new BufferedImage(matrix.getWidth() * scale + 80, matrix.getHeight() * scale + 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = card.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, card.getWidth(), card.getHeight());
        graphics.setColor(Color.BLACK);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    graphics.fillRect(40 + x * scale, 40 + y * scale, scale, scale);
                }
            }
        }
        graphics.dispose();
        return card;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }
}
