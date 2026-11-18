package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoxProjectorTest {

    private final BoxProjector projector = new BoxProjector();

    @Test
    void quebecBoxesFillTheCanvasAndSkipTheDocument() throws Exception {
        List<ProjectedBox> boxes = projector.project(quebec());

        assertEquals(21, boxes.size());
        assertEquals(new ProjectedBox("logo_quebec_texte", "fixed", 27, 24, 154, 44), boxes.get(0));
        for (ProjectedBox box : boxes) {
            assertTrue(box.x() >= 0 && box.y() >= 0, box.name());
            assertTrue(box.x() + box.width() <= 729, box.name());
            assertTrue(box.y() + box.height() <= 457, box.name());
        }
    }

    @Test
    void scalesWhenTheCanvasIsNotTheDocumentSize() {
        AuthenticityTemplate template = new AuthenticityTemplate(
                "SAMPLE_CARD:2019:BACK",
                "SAMPLE_CARD",
                "2019",
                "BACK",
                200,
                120,
                10,
                20,
                100,
                50,
                200,
                100,
                2,
                1,
                0,
                List.of(new SourceBox("mark", "fixed", 30, 40, 10, 10)),
                List.of(),
                List.of(),
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

        List<ProjectedBox> boxes = projector.project(template);

        assertEquals(List.of(new ProjectedBox("mark", "fixed", 40, 40, 20, 20)), boxes);
    }

    @Test
    void rejectsABoxThatLeavesTheCanvas() {
        AuthenticityTemplate template = new AuthenticityTemplate(
                "SAMPLE_CARD:2019:BACK",
                "SAMPLE_CARD",
                "2019",
                "BACK",
                80,
                40,
                10,
                5,
                40,
                20,
                40,
                20,
                2,
                1,
                0,
                List.of(new SourceBox("mark", "fixed", 0, 0, 8, 8)),
                List.of(),
                List.of(),
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> projector.project(template));
        assertTrue(error.getMessage().contains("mark"));
    }

    private static AuthenticityTemplate quebec() throws Exception {
        byte[] image;
        byte[] coco;
        try (InputStream reference = BoxProjectorTest.class.getResourceAsStream(
                        "/authenticity/QUEBEC_DRIVER_LICENSE_2024_FRONT/reference.jpg");
                InputStream annotations = BoxProjectorTest.class.getResourceAsStream(
                        "/authenticity/QUEBEC_DRIVER_LICENSE_2024_FRONT/annotations.coco.json")) {
            image = reference.readAllBytes();
            coco = annotations.readAllBytes();
        }
        return TemplateRegistry.load("QUEBEC_DRIVER_LICENSE_2024_FRONT", image, coco);
    }
}
