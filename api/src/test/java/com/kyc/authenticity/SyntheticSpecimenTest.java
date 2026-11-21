package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Photos dérivées des spécimens. La positive est le même cliché, un peu plus clair.
 * La négative garde le fond de la photo et remplace la carte par un aplat.
 */
class SyntheticSpecimenTest {

    private static final Path ROOT = Path.of("target/authenticity-synthetic");

    private final TemplateRegistry registry = new TemplateRegistry();
    private final CardRectifier rectifier = new CardRectifier();
    private final BoxProjector projector = new BoxProjector();
    private final FixedMatcher fixedMatcher = new FixedMatcher(projector);
    private final PositionMeasurer measurer = new PositionMeasurer(projector);

    @Test
    void positiveStaysConformeAndBlankedMarksDoNot() throws IOException {
        for (AuthenticityTemplate template : registry.templates()) {
            BufferedImage positive = brighten(template.reference(), 1.08);
            BufferedImage negative = blankCard(template);
            write(template, "positive.jpg", positive);
            write(template, "negative.jpg", negative);

            String passed = verdict(template, positive);
            String refused = verdict(template, negative);
            assertEquals("conforme", passed, template.documentId());
            assertNotEquals("conforme", refused, template.documentId() + " " + refused);
        }
    }

    private String verdict(AuthenticityTemplate template, BufferedImage photo) {
        Optional<Rectified> aligned = rectifier.rectify(template, photo);
        if (aligned.isEmpty()) {
            return AuthenticityVerdict.alignmentFailed().verdict();
        }
        List<AuthenticityVerdict.Reading> readings = new ArrayList<>();
        for (FixedScore score : fixedMatcher.match(template, aligned.get().image())) {
            readings.add(new AuthenticityVerdict.Reading(score.status(), null));
        }
        for (PositionScore score : measurer.measure(template, aligned.get().image())) {
            readings.add(new AuthenticityVerdict.Reading(score.status(), score.code()));
        }
        return AuthenticityVerdict.decide(readings).verdict();
    }

    private static BufferedImage brighten(BufferedImage source, double factor) {
        BufferedImage image = copy(source);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                int red = channel((pixel >> 16) & 0xFF, factor);
                int green = channel((pixel >> 8) & 0xFF, factor);
                int blue = channel(pixel & 0xFF, factor);
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return image;
    }

    private static int channel(int value, double factor) {
        return Math.min(255, (int) Math.round(value * factor));
    }

    private static BufferedImage blankCard(AuthenticityTemplate template) {
        BufferedImage image = copy(template.reference());
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(214, 214, 214));
        graphics.fillRect(
                template.documentX(), template.documentY(), template.documentWidth(), template.documentHeight());
        graphics.dispose();
        return image;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return image;
    }

    private static void write(AuthenticityTemplate template, String name, BufferedImage image) throws IOException {
        String folder = template.code() + "_" + template.version() + "_" + template.side();
        Path directory = ROOT.resolve(folder);
        Files.createDirectories(directory);
        ImageIO.write(image, "jpg", directory.resolve(name).toFile());
    }
}
