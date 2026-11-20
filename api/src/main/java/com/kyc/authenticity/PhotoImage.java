package com.kyc.authenticity;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Décode un JPEG ou un PNG et le remet droit selon l'orientation EXIF. */
public final class PhotoImage {

    private PhotoImage() {}

    public static boolean isJpeg(byte[] bytes) {
        return bytes != null
                && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    public static boolean isPng(byte[] bytes) {
        return bytes != null
                && bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    /** 1 lorsque le fichier n'est pas un JPEG ou n'a pas de balise d'orientation. */
    public static int orientation(byte[] jpeg) {
        if (!isJpeg(jpeg)) {
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

    /** Image RVB debout, ou {@code null} si les octets ne se décodent pas. */
    public static BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || (!isJpeg(bytes) && !isPng(bytes))) {
            return null;
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
            return null;
        }
        BufferedImage upright = orient(decoded, orientation(bytes));
        if (upright.getType() == BufferedImage.TYPE_INT_RGB) {
            return upright;
        }
        BufferedImage rgb = new BufferedImage(upright.getWidth(), upright.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(upright, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private static BufferedImage orient(BufferedImage source, int orientation) {
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
                /* orientation 1 */
            }
        }
        return transform;
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
