package com.kyc.authenticity;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Compare chaque élément fixe et chaque libellé du gabarit à la carte déjà redressée.
 * Le crop vient de la référence ramenée au canevas. Il glisse dans la fenêtre élargie de
 * {@link TemplateEngine#SEARCH_MARGIN_PX}. Le statut suit le score, pas le décalage.
 */
@Component
public class FixedMatcher {

    private final BoxProjector projector;

    public FixedMatcher(BoxProjector projector) {
        this.projector = projector;
    }

    public List<FixedScore> match(AuthenticityTemplate template, BufferedImage aligned) {
        if (aligned.getWidth() != template.canvasWidth() || aligned.getHeight() != template.canvasHeight()) {
            throw new IllegalArgumentException("La carte alignée n'a pas la taille du canevas");
        }
        BufferedImage reference = referenceCanvas(template);
        int[] referenceGray = gray(reference);
        int[] alignedGray = gray(aligned);
        int width = template.canvasWidth();
        int margin = TemplateEngine.SEARCH_MARGIN_PX;
        List<FixedScore> scores = new ArrayList<>();
        for (SourceBox target : template.fixedTargets()) {
            ProjectedBox box = projector.projectOne(template, target);
            int[] crop = crop(referenceGray, width, box.x(), box.y(), box.width(), box.height());
            int bestX = box.x();
            int bestY = box.y();
            double best = Double.NEGATIVE_INFINITY;
            int x0 = Math.max(0, box.x() - margin);
            int y0 = Math.max(0, box.y() - margin);
            int x1 = Math.min(width - box.width(), box.x() + margin);
            int y1 = Math.min(template.canvasHeight() - box.height(), box.y() + margin);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double score = similarity(crop, box.width(), box.height(), alignedGray, width, x, y);
                    int shift = Math.max(Math.abs(x - box.x()), Math.abs(y - box.y()));
                    int bestShift = Math.max(Math.abs(bestX - box.x()), Math.abs(bestY - box.y()));
                    if (score > best + 1e-12 || (Math.abs(score - best) <= 1e-12 && shift < bestShift)) {
                        best = score;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
            double rounded = Math.round(best * 10000.0) / 10000.0;
            int shiftPx = Math.max(Math.abs(bestX - box.x()), Math.abs(bestY - box.y()));
            scores.add(new FixedScore(box.name(), box.role(), rounded, shiftPx, status(rounded)));
        }
        return List.copyOf(scores);
    }

    static String status(double score) {
        if (score >= TemplateEngine.SIMILARITY_PASS) {
            return "conforme";
        }
        if (score < TemplateEngine.SIMILARITY_SUSPECT) {
            return "suspect";
        }
        return "a_verifier";
    }

    private static BufferedImage referenceCanvas(AuthenticityTemplate template) {
        BufferedImage crop = template.reference().getSubimage(
                template.documentX(), template.documentY(), template.documentWidth(), template.documentHeight());
        if (crop.getWidth() == template.canvasWidth() && crop.getHeight() == template.canvasHeight()) {
            return crop;
        }
        BufferedImage scaled = new BufferedImage(
                template.canvasWidth(), template.canvasHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(crop, 0, 0, template.canvasWidth(), template.canvasHeight(), null);
        graphics.dispose();
        return scaled;
    }

    private static int[] gray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int[] values = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int red = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int blue = pixel & 0xFF;
            values[i] = (77 * red + 150 * green + 29 * blue) >> 8;
        }
        return values;
    }

    private static int[] crop(int[] image, int imageWidth, int x, int y, int width, int height) {
        int[] values = new int[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(image, (y + row) * imageWidth + x, values, row * width, width);
        }
        return values;
    }

    private static double similarity(
            int[] crop, int cropWidth, int cropHeight, int[] image, int imageWidth, int originX, int originY) {
        int count = cropWidth * cropHeight;
        long sumCrop = 0;
        long sumImage = 0;
        long sumCropCrop = 0;
        long sumImageImage = 0;
        long sumProduct = 0;
        for (int y = 0; y < cropHeight; y++) {
            int cropRow = y * cropWidth;
            int imageRow = (originY + y) * imageWidth + originX;
            for (int x = 0; x < cropWidth; x++) {
                int left = crop[cropRow + x];
                int right = image[imageRow + x];
                sumCrop += left;
                sumImage += right;
                sumCropCrop += (long) left * left;
                sumImageImage += (long) right * right;
                sumProduct += (long) left * right;
            }
        }
        double n = count;
        double meanCrop = sumCrop / n;
        double meanImage = sumImage / n;
        double varCrop = Math.max(0, sumCropCrop / n - meanCrop * meanCrop);
        double varImage = Math.max(0, sumImageImage / n - meanImage * meanImage);
        double covariance = sumProduct / n - meanCrop * meanImage;
        if ("SSIM".equals(TemplateEngine.SIMILARITY)) {
            double c1 = 6.5025;
            double c2 = 58.5225;
            double numerator = (2 * meanCrop * meanImage + c1) * (2 * covariance + c2);
            double denominator = (meanCrop * meanCrop + meanImage * meanImage + c1) * (varCrop + varImage + c2);
            return clamp(numerator / denominator, 0, 1);
        }
        if (varCrop < 1e-8 || varImage < 1e-8) {
            return varCrop < 1e-8 && varImage < 1e-8 && Math.abs(meanCrop - meanImage) <= 1 ? 1 : 0;
        }
        return clamp(covariance / Math.sqrt(varCrop * varImage), -1, 1);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
