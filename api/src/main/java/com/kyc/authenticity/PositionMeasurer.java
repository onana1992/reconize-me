package com.kyc.authenticity;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Place le contenu de chaque champ {@code value} et de chaque {@code region} sur la carte redressée.
 * La fenêtre dépasse la boîte projetée de {@link TemplateEngine#MAX_SHIFT_PX}. Le contenu est le rectangle
 * de l'encre, ou du contenu visible pour une région. L'IoU et l'écart des bords décident du statut.
 */
@Component
public class PositionMeasurer {

    private static final double FLAT_VARIANCE = 4;
    private static final int VALUE_INK_GAP = 36;
    private static final int REGION_INK_GAP = 18;
    private static final int MIN_INK_PIXELS = 8;

    private final BoxProjector projector;

    public PositionMeasurer(BoxProjector projector) {
        this.projector = projector;
    }

    public List<PositionScore> measure(AuthenticityTemplate template, BufferedImage aligned) {
        if (aligned.getWidth() != template.canvasWidth() || aligned.getHeight() != template.canvasHeight()) {
            throw new IllegalArgumentException("La carte alignée n'a pas la taille du canevas");
        }
        int[] alignedGray = gray(aligned);
        int width = template.canvasWidth();
        int height = template.canvasHeight();
        List<PositionScore> scores = new ArrayList<>();
        for (SourceBox target : template.positionTargets()) {
            scores.add(measureOne(projector.projectOne(template, target), alignedGray, width, height));
        }
        return List.copyOf(scores);
    }

    static String status(double iou, int deltaPx) {
        if (iou >= TemplateEngine.IOU_PASS && deltaPx < TemplateEngine.MAX_SHIFT_PX) {
            return "conforme";
        }
        if (iou < TemplateEngine.IOU_SUSPECT || deltaPx >= TemplateEngine.MAX_SHIFT_PX) {
            return "suspect";
        }
        return "a_verifier";
    }

    private static PositionScore measureOne(ProjectedBox box, int[] alignedGray, int width, int height) {
        int margin = TemplateEngine.MAX_SHIFT_PX;
        int x0 = Math.max(0, box.x() - margin);
        int y0 = Math.max(0, box.y() - margin);
        int x1 = Math.min(width, box.x() + box.width() + margin);
        int y1 = Math.min(height, box.y() + box.height() + margin);
        int windowWidth = x1 - x0;
        int windowHeight = y1 - y0;
        if (!separable(alignedGray, width, x0, y0, windowWidth, windowHeight)) {
            return new PositionScore(box.name(), box.role(), 0, 0, "a_verifier", "zone_unmeasured");
        }
        int[] found = contentBox(alignedGray, width, x0, y0, x1, y1, box);
        if (found == null) {
            return new PositionScore(box.name(), box.role(), 0, 0, "suspect", "field_absent");
        }
        double iou = Math.round(iou(box.x(), box.y(), box.width(), box.height(), found[0], found[1], found[2], found[3]) * 10000.0)
                / 10000.0;
        int deltaPx = Math.max(
                Math.max(Math.abs(found[0] - box.x()), Math.abs(found[1] - box.y())),
                Math.max(Math.abs(found[2] - box.width()), Math.abs(found[3] - box.height())));
        return new PositionScore(box.name(), box.role(), iou, deltaPx, status(iou, deltaPx), null);
    }

    /**
     * Rectangle du contenu dans la fenêtre. Les composantes qui coupent la boîte attendue sont réunies.
     * S'il n'y en a pas, la composante la plus proche, encore dans {@code maxShiftPx}, est retenue.
     */
    private static int[] contentBox(
            int[] image, int imageWidth, int x0, int y0, int x1, int y1, ProjectedBox box) {
        int windowWidth = x1 - x0;
        int windowHeight = y1 - y0;
        int paper = paperLevel(image, imageWidth, x0, y0, x1, y1, box);
        int gap = "region".equals(box.role()) ? REGION_INK_GAP : VALUE_INK_GAP;
        // Le plafond écarte le fond de sécurité, plus clair que l'encre et que la photo.
        int ceiling = "region".equals(box.role()) ? 175 : 110;
        int threshold = Math.min(paper - gap, ceiling);
        byte[] ink = new byte[windowWidth * windowHeight];
        int marks = 0;
        for (int y = y0; y < y1; y++) {
            int row = y * imageWidth;
            int maskRow = (y - y0) * windowWidth;
            for (int x = x0; x < x1; x++) {
                if (image[row + x] <= threshold) {
                    ink[maskRow + (x - x0)] = 1;
                    marks++;
                }
            }
        }
        if (marks < MIN_INK_PIXELS) {
            return null;
        }
        int[] labels = new int[ink.length];
        List<int[]> components = new ArrayList<>();
        int[] queue = new int[ink.length];
        for (int start = 0; start < ink.length; start++) {
            if (ink[start] == 0 || labels[start] != 0) {
                continue;
            }
            int id = components.size() + 1;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            labels[start] = id;
            int minX = start % windowWidth;
            int maxX = minX;
            int minY = start / windowWidth;
            int maxY = minY;
            int count = 0;
            while (head < tail) {
                int index = queue[head++];
                int px = index % windowWidth;
                int py = index / windowWidth;
                count++;
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
                if (px > 0) {
                    tail = enqueue(ink, labels, queue, tail, index - 1, id);
                }
                if (px + 1 < windowWidth) {
                    tail = enqueue(ink, labels, queue, tail, index + 1, id);
                }
                if (py > 0) {
                    tail = enqueue(ink, labels, queue, tail, index - windowWidth, id);
                }
                if (py + 1 < windowHeight) {
                    tail = enqueue(ink, labels, queue, tail, index + windowWidth, id);
                }
            }
            if (count >= MIN_INK_PIXELS) {
                components.add(new int[] {x0 + minX, y0 + minY, maxX - minX + 1, maxY - minY + 1});
            }
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        boolean overlapping = false;
        int[] nearest = null;
        int nearestGap = Integer.MAX_VALUE;
        for (int[] component : components) {
            if (intersects(component, box)) {
                overlapping = true;
                left = Math.min(left, component[0]);
                top = Math.min(top, component[1]);
                right = Math.max(right, component[0] + component[2]);
                bottom = Math.max(bottom, component[1] + component[3]);
            } else {
                int gapToBox = separation(component, box);
                if (gapToBox < nearestGap) {
                    nearestGap = gapToBox;
                    nearest = component;
                }
            }
        }
        if (overlapping) {
            return new int[] {left, top, right - left, bottom - top};
        }
        if (nearest != null && nearestGap <= TemplateEngine.MAX_SHIFT_PX) {
            return nearest;
        }
        return null;
    }

    private static int enqueue(byte[] ink, int[] labels, int[] queue, int tail, int index, int id) {
        if (ink[index] == 0 || labels[index] != 0) {
            return tail;
        }
        labels[index] = id;
        queue[tail] = index;
        return tail + 1;
    }

    private static boolean intersects(int[] component, ProjectedBox box) {
        int right = component[0] + component[2];
        int bottom = component[1] + component[3];
        return component[0] < box.x() + box.width()
                && right > box.x()
                && component[1] < box.y() + box.height()
                && bottom > box.y();
    }

    private static int separation(int[] component, ProjectedBox box) {
        int gapX = 0;
        if (component[0] + component[2] <= box.x()) {
            gapX = box.x() - (component[0] + component[2]);
        } else if (component[0] >= box.x() + box.width()) {
            gapX = component[0] - (box.x() + box.width());
        }
        int gapY = 0;
        if (component[1] + component[3] <= box.y()) {
            gapY = box.y() - (component[1] + component[3]);
        } else if (component[1] >= box.y() + box.height()) {
            gapY = component[1] - (box.y() + box.height());
        }
        return Math.max(gapX, gapY);
    }

    /** Niveau de papier : percentile haut de l'anneau autour de la boîte, là où le fond de carte domine. */
    private static int paperLevel(int[] image, int imageWidth, int x0, int y0, int x1, int y1, ProjectedBox box) {
        int[] histogram = new int[256];
        int total = 0;
        for (int y = y0; y < y1; y++) {
            boolean insideY = y >= box.y() && y < box.y() + box.height();
            int row = y * imageWidth;
            for (int x = x0; x < x1; x++) {
                if (insideY && x >= box.x() && x < box.x() + box.width()) {
                    continue;
                }
                histogram[image[row + x]]++;
                total++;
            }
        }
        if (total < 16) {
            Arrays.fill(histogram, 0);
            total = 0;
            for (int y = y0; y < y1; y++) {
                int row = y * imageWidth;
                for (int x = x0; x < x1; x++) {
                    histogram[image[row + x]]++;
                    total++;
                }
            }
        }
        int target = Math.max(1, (int) Math.ceil(total * 0.80));
        int seen = 0;
        for (int level = 0; level < histogram.length; level++) {
            seen += histogram[level];
            if (seen >= target) {
                return level;
            }
        }
        return 255;
    }

    static double iou(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        int left = Math.max(ax, bx);
        int top = Math.max(ay, by);
        int right = Math.min(ax + aw, bx + bw);
        int bottom = Math.min(ay + ah, by + bh);
        long intersection = (long) Math.max(0, right - left) * Math.max(0, bottom - top);
        long union = (long) aw * ah + (long) bw * bh - intersection;
        if (union <= 0) {
            return 0;
        }
        return intersection / (double) union;
    }

    private static boolean separable(int[] pixels, int imageWidth, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        long sum = 0;
        long sumSquares = 0;
        int count = width * height;
        for (int row = 0; row < height; row++) {
            int start = (y + row) * imageWidth + x;
            for (int column = 0; column < width; column++) {
                int value = pixels[start + column];
                sum += value;
                sumSquares += (long) value * value;
            }
        }
        double mean = sum / (double) count;
        double variance = sumSquares / (double) count - mean * mean;
        return variance >= FLAT_VARIANCE;
    }

    private static int[] gray(java.awt.image.BufferedImage image) {
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
}
