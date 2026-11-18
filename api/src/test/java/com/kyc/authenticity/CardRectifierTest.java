package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.junit.jupiter.api.Test;

class CardRectifierTest {

    private final CardRectifier rectifier = new CardRectifier();

    @Test
    void referenceLandsOnTheDerivedCanvas() throws Exception {
        AuthenticityTemplate template = quebec();
        Optional<Rectified> aligned = rectifier.rectify(template, template.reference());

        assertTrue(aligned.isPresent());
        Rectified rectified = aligned.get();
        assertTrue(rectified.method().equals("corners") || rectified.method().equals("features"));
        assertEquals(729, rectified.image().getWidth());
        assertEquals(457, rectified.image().getHeight());
        BufferedImage crop = template.reference().getSubimage(287, 185, 729, 457);
        double score = ncc(rectified.image(), crop);
        assertTrue(score >= 0.95, rectified.method() + "/" + rectified.detector() + " ncc=" + score);
    }

    @Test
    void perspectiveOfTheReferenceReturnsToTheSameCanvas() throws Exception {
        AuthenticityTemplate template = quebec();
        BufferedImage warped = perspective(template.reference());
        Optional<Rectified> aligned = rectifier.rectify(template, warped);

        assertTrue(aligned.isPresent());
        assertTrue(aligned.get().method().equals("corners") || aligned.get().method().equals("features"));
        assertEquals(729, aligned.get().image().getWidth());
        assertEquals(457, aligned.get().image().getHeight());
        BufferedImage crop = template.reference().getSubimage(287, 185, 729, 457);
        double score = ncc(aligned.get().image(), crop);
        assertTrue(score >= 0.85, "ncc=" + score);
    }

    @Test
    void aQuadThatMissesThePrintedMarksFallsThroughToFeatures() throws Exception {
        AuthenticityTemplate template = quebec();
        BufferedImage document = template.reference().getSubimage(287, 185, 729, 457);
        BufferedImage photo = new BufferedImage(1900, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = photo.createGraphics();
        graphics.setColor(new Color(30, 30, 30));
        graphics.fillRect(0, 0, photo.getWidth(), photo.getHeight());
        graphics.setColor(Color.WHITE);
        graphics.fillRect(20, 20, 1000, 627);
        for (int y = 20; y < 647; y++) {
            int level = 80 + (y % 40);
            graphics.setColor(new Color(level, level, level));
            graphics.drawLine(20, y, 1019, y);
        }
        graphics.drawImage(document, 1100, 80, null);
        graphics.dispose();

        Rectified aligned = rectifier.rectify(template, photo).orElseThrow();
        assertEquals("features", aligned.method());
        assertTrue(ncc(aligned.image(), document) >= 0.85, aligned.detector() + " ncc");
    }

    @Test
    void flatImageDoesNotAlign() throws Exception {
        BufferedImage gray = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = gray.createGraphics();
        graphics.setColor(new Color(180, 180, 180));
        graphics.fillRect(0, 0, 640, 400);
        graphics.dispose();

        assertTrue(rectifier.rectify(quebec(), gray).isEmpty());
    }

    @Test
    void sameImageGivesTheSameMethod() throws Exception {
        AuthenticityTemplate template = quebec();
        Rectified first = rectifier.rectify(template, template.reference()).orElseThrow();
        Rectified second = rectifier.rectify(template, template.reference()).orElseThrow();
        assertEquals(first.method(), second.method());
        assertEquals(first.detector(), second.detector());
        assertEquals(first.image().getRGB(10, 10), second.image().getRGB(10, 10));
        assertEquals(first.image().getRGB(700, 400), second.image().getRGB(700, 400));
    }

    @Test
    void anotherEditionUsesItsOwnCanvas() {
        BufferedImage plate = TemplateRegistryTest.plate(200, 120, 20, 10, 160, 100);
        AuthenticityTemplate template = new AuthenticityTemplate(
                "SAMPLE_CARD:2019:BACK",
                "SAMPLE_CARD",
                "2019",
                "BACK",
                200,
                120,
                20,
                10,
                160,
                100,
                160,
                100,
                1.6,
                1,
                0,
                List.of(),
                List.of(),
                List.of(),
                plate);
        Rectified aligned = rectifier.rectify(template, plate).orElseThrow();
        assertEquals(160, aligned.image().getWidth());
        assertEquals(100, aligned.image().getHeight());
        assertTrue(ncc(aligned.image(), plate.getSubimage(20, 10, 160, 100)) >= 0.95);
    }

    private static AuthenticityTemplate quebec() throws Exception {
        byte[] image;
        byte[] coco;
        try (InputStream reference = CardRectifierTest.class.getResourceAsStream(
                        "/authenticity/QUEBEC_DRIVER_LICENSE_2024_FRONT/reference.jpg");
                InputStream annotations = CardRectifierTest.class.getResourceAsStream(
                        "/authenticity/QUEBEC_DRIVER_LICENSE_2024_FRONT/annotations.coco.json")) {
            image = reference.readAllBytes();
            coco = annotations.readAllBytes();
        }
        return TemplateRegistry.load("QUEBEC_DRIVER_LICENSE_2024_FRONT", image, coco);
    }

    private static BufferedImage perspective(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Mat color = CardRectifier.bgr(image);
        Mat source = CardRectifierPoints.of(0, 0, width - 1, 0, width - 1, height - 1, 0, height - 1);
        Mat destination = CardRectifierPoints.of(
                0.08 * width,
                0.03 * height,
                width - 1 - 0.04 * width,
                0.08 * height,
                width - 1 - 0.08 * width,
                height - 1 - 0.02 * height,
                0.05 * width,
                height - 1 - 0.07 * height);
        Mat transform = opencv_imgproc.getPerspectiveTransform(source, destination);
        Mat output = new Mat();
        try {
            opencv_imgproc.warpPerspective(
                    color,
                    output,
                    transform,
                    new Size(width, height),
                    opencv_imgproc.INTER_LINEAR,
                    opencv_core.BORDER_CONSTANT,
                    new Scalar(180, 180, 180, 0));
            return rgb(output);
        } finally {
            color.close();
            source.close();
            destination.close();
            transform.close();
            output.close();
        }
    }

    private static double ncc(BufferedImage left, BufferedImage right) {
        assertEquals(left.getWidth(), right.getWidth());
        assertEquals(left.getHeight(), right.getHeight());
        int count = left.getWidth() * left.getHeight();
        double[] a = gray(left);
        double[] b = gray(right);
        double meanA = mean(a);
        double meanB = mean(b);
        double product = 0;
        double squareA = 0;
        double squareB = 0;
        for (int i = 0; i < count; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            product += da * db;
            squareA += da * da;
            squareB += db * db;
        }
        return product / Math.sqrt(squareA * squareB);
    }

    private static double[] gray(BufferedImage image) {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        double[] values = new double[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int red = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int blue = pixel & 0xFF;
            values[i] = 0.299 * red + 0.587 * green + 0.114 * blue;
        }
        return values;
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static BufferedImage rgb(Mat bgr) {
        int width = bgr.cols();
        int height = bgr.rows();
        byte[] data = new byte[width * height * 3];
        bgr.data().get(data);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int blue = data[i * 3] & 0xFF;
            int green = data[i * 3 + 1] & 0xFF;
            int red = data[i * 3 + 2] & 0xFF;
            pixels[i] = (red << 16) | (green << 8) | blue;
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    /** Points du test, pour ne pas exposer le helper du redresseur. */
    private static final class CardRectifierPoints {
        private CardRectifierPoints() {}

        static Mat of(double... coordinates) {
            Mat mat = new Mat(coordinates.length / 2, 1, opencv_core.CV_32FC2);
            try (org.bytedeco.javacpp.indexer.FloatIndexer indexer = mat.createIndexer()) {
                for (int i = 0; i < coordinates.length; i += 2) {
                    indexer.put(i / 2, 0, 0, (float) coordinates[i]);
                    indexer.put(i / 2, 0, 1, (float) coordinates[i + 1]);
                }
            }
            return mat;
        }
    }
}
