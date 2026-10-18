package com.kyc.services;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class MediaQuality {

    public static final long MAX_BYTES = 10 * 1024 * 1024;
    public static final int MIN_SHORT_SIDE = 720;

    private MediaQuality() {}

    public static String sniff(byte[] body) {
        if (body.length >= 3 && body[0] == (byte) 0xFF && body[1] == (byte) 0xD8 && body[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (body.length >= 8
                && body[0] == (byte) 0x89
                && body[1] == 0x50
                && body[2] == 0x4E
                && body[3] == 0x47) {
            return "image/png";
        }
        return null;
    }

    public static boolean acceptableDocument(byte[] body) {
        return rejectionReason(body) == null;
    }

    /** Cause réelle du refus média, ou {@code null} si l'image peut être analysée. */
    public static String rejectionReason(byte[] body) {
        if (body == null || body.length == 0) {
            return "INVALID_IMAGE";
        }
        if (body.length > MAX_BYTES) {
            return "TOO_LARGE";
        }
        if (sniff(body) == null) {
            return "INVALID_IMAGE";
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
            if (image == null) {
                return "INVALID_IMAGE";
            }
            int shortSide = Math.min(image.getWidth(), image.getHeight());
            if (shortSide < MIN_SHORT_SIDE) {
                return "TOO_SMALL";
            }
            return null;
        } catch (IOException e) {
            return "INVALID_IMAGE";
        }
    }

    public static boolean acceptableSelfie(byte[] body) {
        return acceptableDocument(body);
    }
}
