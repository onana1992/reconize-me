package com.kyc.services.documentia;

import com.kyc.dto.documentia.DocumentImageQuality;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class ImageQualityGate {

    static final int MIN_SHORT_SIDE = 480;
    static final double DARK = 22;
    static final double BRIGHT = 250;
    static final double MIN_SHARPNESS = 4;
    private static final int ANALYSIS_SHORT_SIDE = 320;

    public DocumentImageQuality assess(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw invalid();
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw invalid();
            }
            return assess(image);
        } catch (IOException e) {
            throw invalid();
        }
    }

    public DocumentImageQuality assess(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean tooSmall = Math.min(width, height) < MIN_SHORT_SIDE;
        Sample sample = sample(image, width, height);
        boolean lowLight = sample.mean < DARK;
        boolean overexposed = sample.mean > BRIGHT;
        boolean blur = sample.sharpness < MIN_SHARPNESS;
        boolean readable = !tooSmall && !lowLight && !overexposed && !blur;
        return new DocumentImageQuality(
                readable,
                blur,
                false,
                false,
                false,
                false,
                tooSmall,
                lowLight,
                overexposed,
                false,
                !readable,
                reason(tooSmall, lowLight, overexposed, blur));
    }

    private static Sample sample(BufferedImage image, int width, int height) {
        int step = Math.max(1, (int) Math.round(Math.min(width, height) / (double) ANALYSIS_SHORT_SIDE));
        int sampleWidth = Math.max(1, width / step);
        int sampleHeight = Math.max(1, height / step);
        double[] luma = new double[sampleWidth * sampleHeight];
        double sum = 0;
        for (int gy = 0; gy < sampleHeight; gy++) {
            int y = Math.min(height - 1, gy * step);
            for (int gx = 0; gx < sampleWidth; gx++) {
                int x = Math.min(width - 1, gx * step);
                double value = luminance(image.getRGB(x, y));
                luma[gy * sampleWidth + gx] = value;
                sum += value;
            }
        }
        double mean = sum / luma.length;
        return new Sample(mean, sharpestTile(luma, sampleWidth, sampleHeight));
    }

    /** Le verso d'une carte est surtout pâle. La MRZ ou le code tient dans une bande : c'est elle qui compte. */
    private static double sharpestTile(double[] luma, int width, int height) {
        int tiles = 4;
        double best = 0;
        int tileW = Math.max(1, width / tiles);
        int tileH = Math.max(1, height / tiles);
        for (int ty = 0; ty < tiles; ty++) {
            int y0 = ty * tileH;
            int y1 = ty == tiles - 1 ? height : Math.min(height, y0 + tileH);
            for (int tx = 0; tx < tiles; tx++) {
                int x0 = tx * tileW;
                int x1 = tx == tiles - 1 ? width : Math.min(width, x0 + tileW);
                double edge = 0;
                int samples = 0;
                for (int y = Math.max(1, y0); y < Math.min(height - 1, y1); y++) {
                    for (int x = Math.max(1, x0); x < Math.min(width - 1, x1); x++) {
                        double center = luma[y * width + x];
                        double lap = Math.abs(center - luma[y * width + (x - 1)])
                                + Math.abs(center - luma[y * width + (x + 1)])
                                + Math.abs(center - luma[(y - 1) * width + x])
                                + Math.abs(center - luma[(y + 1) * width + x]);
                        edge += lap / 4;
                        samples++;
                    }
                }
                if (samples > 0) {
                    best = Math.max(best, edge / samples);
                }
            }
        }
        return best;
    }

    private static double luminance(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return 0.299 * red + 0.587 * green + 0.114 * blue;
    }

    private static String reason(boolean tooSmall, boolean lowLight, boolean overexposed, boolean blur) {
        if (tooSmall) {
            return "TOO_SMALL";
        }
        if (lowLight) {
            return "LOW_LIGHT";
        }
        if (overexposed) {
            return "OVEREXPOSED";
        }
        if (blur) {
            return "IMAGE_TOO_BLURRY";
        }
        return null;
    }

    private static ApiException invalid() {
        return ApiException.validation("Image could not be read", List.of(new ErrorDetail("file", "invalid")));
    }

    private record Sample(double mean, double sharpness) {}
}
