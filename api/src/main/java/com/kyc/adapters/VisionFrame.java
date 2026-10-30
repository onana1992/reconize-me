package com.kyc.adapters;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Le modèle vision ramène le petit côté à 768 px avant de lire.
 * Au-delà de {@link #MAX_LONG_SIDE}, les pixels supplémentaires n'ajoutent que du temps d'envoi.
 */
final class VisionFrame {

    static final int MAX_LONG_SIDE = 1280;
    private static final float JPEG_QUALITY = 0.8f;

    private VisionFrame() {}

    static byte[] fit(byte[] image) {
        if (image == null || image.length == 0) {
            return image;
        }
        int[] stored = jpegSize(image);
        if (stored != null && Math.max(stored[0], stored[1]) <= MAX_LONG_SIDE) {
            return image;
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            return image;
        }
        if (decoded == null) {
            return image;
        }
        int orientation = orientation(image);
        int longSide = Math.max(decoded.getWidth(), decoded.getHeight());
        if (longSide <= MAX_LONG_SIDE) {
            return image;
        }
        BufferedImage upright = orient(decoded, orientation);
        byte[] jpeg = writeJpeg(scale(upright, MAX_LONG_SIDE));
        return jpeg == null || jpeg.length == 0 ? image : jpeg;
    }

    private static BufferedImage scale(BufferedImage source, int maxLongSide) {
        double factor = maxLongSide / (double) Math.max(source.getWidth(), source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage destination =
                new BufferedImage(swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = destination.createGraphics();
        graphics.setTransform(transform(orientation, width, height));
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return destination;
    }

    private static AffineTransform transform(int orientation, int width, int height) {
        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> {
                transform.translate(width, 0);
                transform.scale(-1, 1);
            }
            case 3 -> {
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> {
                transform.translate(0, height);
                transform.scale(1, -1);
            }
            case 5 -> {
                transform.rotate(-Math.PI / 2);
                transform.scale(-1, 1);
            }
            case 6 -> {
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> {
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
                transform.scale(-1, 1);
            }
            case 8 -> {
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
            }
            default -> {
                /* orientation 1 : déjà redressée */
            }
        }
        return transform;
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
            return bytes.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            writer.dispose();
        }
    }

    /** Largeur et hauteur stockées, sans décoder les pixels. {@code null} si ce n'est pas un JPEG lisible. */
    private static int[] jpegSize(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return null;
        }
        int index = 2;
        while (index + 9 < jpeg.length) {
            if ((jpeg[index] & 0xFF) != 0xFF) {
                return null;
            }
            int marker = jpeg[index + 1] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) {
                return null;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {
                index += 2;
                continue;
            }
            int length = ((jpeg[index + 2] & 0xFF) << 8) | (jpeg[index + 3] & 0xFF);
            if (length < 2 || index + 2 + length > jpeg.length) {
                return null;
            }
            if ((marker >= 0xC0 && marker <= 0xC3) || (marker >= 0xC5 && marker <= 0xC7) || (marker >= 0xC9 && marker <= 0xCB) || (marker >= 0xCD && marker <= 0xCF)) {
                int height = ((jpeg[index + 5] & 0xFF) << 8) | (jpeg[index + 6] & 0xFF);
                int width = ((jpeg[index + 7] & 0xFF) << 8) | (jpeg[index + 8] & 0xFF);
                if (width <= 0 || height <= 0) {
                    return null;
                }
                return new int[] {width, height};
            }
            index += 2 + length;
        }
        return null;
    }

    /** 1 si le fichier n'est pas un JPEG ou n'a pas de balise d'orientation. */
    static int orientation(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int index = 2;
        while (index + 4 < jpeg.length) {
            if ((jpeg[index] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = jpeg[index + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                return 1;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {
                index += 2;
                continue;
            }
            int length = ((jpeg[index + 2] & 0xFF) << 8) | (jpeg[index + 3] & 0xFF);
            if (length < 2 || index + 2 + length > jpeg.length) {
                return 1;
            }
            if (marker == 0xE1) {
                int value = exifOrientation(jpeg, index + 4, length - 2);
                if (value >= 1 && value <= 8) {
                    return value;
                }
            }
            index += 2 + length;
        }
        return 1;
    }

    private static int exifOrientation(byte[] data, int offset, int length) {
        if (length < 16
                || data[offset] != 'E'
                || data[offset + 1] != 'x'
                || data[offset + 2] != 'i'
                || data[offset + 3] != 'f'
                || data[offset + 4] != 0
                || data[offset + 5] != 0) {
            return 0;
        }
        int tiff = offset + 6;
        int end = offset + length;
        if (tiff + 8 > end) {
            return 0;
        }
        boolean little = data[tiff] == 'I' && data[tiff + 1] == 'I';
        boolean big = data[tiff] == 'M' && data[tiff + 1] == 'M';
        if (!little && !big) {
            return 0;
        }
        int ifd = tiff + u32(data, tiff + 4, little);
        if (ifd < tiff || ifd + 2 > end) {
            return 0;
        }
        int count = u16(data, ifd, little);
        for (int entry = 0; entry < count; entry++) {
            int position = ifd + 2 + entry * 12;
            if (position + 12 > end) {
                return 0;
            }
            if (u16(data, position, little) == 0x0112) {
                return u16(data, position + 8, little);
            }
        }
        return 0;
    }

    private static int u16(byte[] data, int offset, boolean little) {
        int low = data[offset] & 0xFF;
        int high = data[offset + 1] & 0xFF;
        return little ? (high << 8) | low : (low << 8) | high;
    }

    private static int u32(byte[] data, int offset, boolean little) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;
        return little ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0 : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }
}
