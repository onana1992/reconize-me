package com.kyc.adapters;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.pdf417.PDF417Reader;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import javax.imageio.ImageIO;

/** Présence d'un PDF417. Un échec laisse la lecture au modèle. */
public final class Pdf417Barcode {

    private static final int MAX_LONG_SIDE = 1600;

    private Pdf417Barcode() {}

    public static boolean present(byte[] image) {
        if (image == null || image.length == 0) {
            return false;
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            return false;
        }
        if (decoded == null) {
            return false;
        }
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(BarcodeFormat.PDF_417));
        try {
            new PDF417Reader().decode(new BinaryBitmap(new HybridBinarizer(new RgbLuminance(scale(decoded)))), hints);
            return true;
        } catch (ReaderException e) {
            return false;
        }
    }

    private static BufferedImage scale(BufferedImage image) {
        int longSide = Math.max(image.getWidth(), image.getHeight());
        if (longSide <= MAX_LONG_SIDE) {
            return image;
        }
        double factor = (double) MAX_LONG_SIDE / longSide;
        int width = Math.max(1, (int) Math.round(image.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(image.getHeight() * factor));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return out;
    }

    private static final class RgbLuminance extends LuminanceSource {

        private final byte[] luminances;
        private final int width;

        RgbLuminance(BufferedImage image) {
            super(image.getWidth(), image.getHeight());
            width = image.getWidth();
            int height = image.getHeight();
            luminances = new byte[width * height];
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;
                luminances[i] = (byte) ((red + (green << 1) + blue) >> 2);
            }
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            if (row == null || row.length < width) {
                row = new byte[width];
            }
            System.arraycopy(luminances, y * width, row, 0, width);
            return row;
        }

        @Override
        public byte[] getMatrix() {
            return luminances;
        }
    }
}
