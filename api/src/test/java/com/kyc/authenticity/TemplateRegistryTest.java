package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateRegistryTest {

    @Test
    void loadsTheShippedGabarits() {
        List<String> ids = new TemplateRegistry().templates().stream()
                .map(AuthenticityTemplate::documentId)
                .toList();

        assertEquals(
                List.of(
                        "CANADA_PERMANENT_RESIDENT:2015:BACK",
                        "CANADA_PERMANENT_RESIDENT:2015:FRONT",
                        "QUEBEC_DRIVER_LICENSE:2024:BACK",
                        "QUEBEC_DRIVER_LICENSE:2024:FRONT"),
                ids);
    }

    @Test
    void derivesCanvasFromDocumentBox(@TempDir Path directory) throws Exception {
        Path folder = directory.resolve("SAMPLE_CARD_2019_BACK");
        Files.createDirectories(folder);
        ImageIO.write(plate(200, 120, 20, 10, 160, 100), "png", folder.resolve("reference.png").toFile());
        Files.writeString(folder.resolve("annotations.coco.json"), coco(200, 120, 20, 10, 160, 100));

        AuthenticityTemplate template = TemplateRegistry.readDirectory(folder);

        assertEquals("SAMPLE_CARD:2019:BACK", template.documentId());
        assertEquals("SAMPLE_CARD", template.code());
        assertEquals("2019", template.version());
        assertEquals("BACK", template.side());
        assertEquals(160, template.canvasWidth());
        assertEquals(100, template.canvasHeight());
        assertEquals(1, template.fixedCount());
        assertEquals(0, template.fieldCount());
        assertEquals(160 / 100.0, template.ratio());
        assertEquals(1, template.boxes().size());
        assertEquals("mark", template.boxes().get(0).name());
        assertEquals("fixed", template.boxes().get(0).role());
        assertEquals(26, template.boxes().get(0).x());
        assertEquals(16, template.boxes().get(0).y());
        assertEquals(1, template.fixedTargets().size());
        assertEquals("mark", template.fixedTargets().get(0).name());
        assertEquals(0, template.positionTargets().size());
    }

    @Test
    void positionTargetPrefersTheValueBox(@TempDir Path directory) throws Exception {
        Path folder = directory.resolve("SAMPLE_CARD_2019_FRONT");
        Files.createDirectories(folder);
        ImageIO.write(plate(80, 40, 0, 0, 80, 40), "png", folder.resolve("reference.png").toFile());
        Files.writeString(folder.resolve("annotations.coco.json"), """
                {
                  "images": [{"id": 1, "file_name": "ignored.png", "width": 80, "height": 40}],
                  "categories": [
                    {"id": 1, "name": "document", "role": "document"},
                    {"id": 2, "name": "nom", "role": "value"},
                    {"id": 3, "name": "photo", "role": "region"}
                  ],
                  "annotations": [
                    {"id": 1, "image_id": 1, "category_id": 1, "bbox": [0, 0, 80, 40]},
                    {"id": 2, "image_id": 1, "category_id": 2, "bbox": [10, 10, 40, 20],
                      "attributes": {"bbox_value": [12, 12, 20, 8], "bbox_label": null}},
                    {"id": 3, "image_id": 1, "category_id": 3, "bbox": [50, 10, 20, 20]}
                  ]
                }
                """);

        List<SourceBox> targets = TemplateRegistry.readDirectory(folder).positionTargets();

        assertEquals(2, targets.size());
        assertEquals(new SourceBox("nom", "value", 12, 12, 20, 8), targets.get(0));
        assertEquals(new SourceBox("photo", "region", 50, 10, 20, 20), targets.get(1));
    }

    @Test
    void rejectsABoxOutsideTheDocument(@TempDir Path directory) throws Exception {
        Path folder = directory.resolve("SAMPLE_CARD_2019_FRONT");
        Files.createDirectories(folder);
        ImageIO.write(plate(80, 40, 10, 5, 40, 20), "png", folder.resolve("reference.png").toFile());
        String coco = """
                {
                  "images": [{"id": 1, "file_name": "ignored.png", "width": 80, "height": 40}],
                  "categories": [
                    {"id": 1, "name": "document", "role": "document"},
                    {"id": 2, "name": "mark", "role": "fixed"}
                  ],
                  "annotations": [
                    {"id": 1, "image_id": 1, "category_id": 1, "bbox": [10, 5, 40, 20]},
                    {"id": 2, "image_id": 1, "category_id": 2, "bbox": [0, 0, 8, 8]}
                  ]
                }
                """;
        Files.writeString(folder.resolve("annotations.coco.json"), coco);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> TemplateRegistry.readDirectory(folder));
        assertTrue(error.getMessage().contains("SAMPLE_CARD_2019_FRONT"));
        assertTrue(error.getMessage().contains("document"));
    }

    @Test
    void rejectsTwoReferenceFiles(@TempDir Path directory) throws Exception {
        Path folder = directory.resolve("SAMPLE_CARD_2019_FRONT");
        Files.createDirectories(folder);
        Files.write(folder.resolve("reference.jpg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        Files.write(folder.resolve("reference.png"), new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
        Files.writeString(folder.resolve("annotations.coco.json"), "{}");

        assertThrows(IllegalStateException.class, () -> TemplateRegistry.readDirectory(folder));
    }

    @Test
    void rejectsADuplicateDocumentId() {
        AuthenticityTemplate first = template("SAMPLE:2019:FRONT");
        AuthenticityTemplate second = template("SAMPLE:2019:FRONT");
        assertThrows(IllegalStateException.class, () -> TemplateRegistry.index(List.of(first, second)));
    }

    private static AuthenticityTemplate template(String documentId) {
        return new AuthenticityTemplate(
                documentId,
                "SAMPLE",
                "2019",
                "FRONT",
                10,
                10,
                0,
                0,
                10,
                8,
                10,
                8,
                1.25,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                SpecimenPicture.held(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)));
    }

    static BufferedImage plate(int width, int height, int x, int y, int cardWidth, int cardHeight) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                boolean card = column >= x && column < x + cardWidth && row >= y && row < y + cardHeight;
                boolean frame = card
                        && (column < x + 4 || row < y + 4 || column >= x + cardWidth - 4 || row >= y + cardHeight - 4);
                int value = frame ? 245 : card ? 120 + (int) (50 * Math.sin(column / 28.0) * Math.cos(row / 23.0)) : 32;
                image.setRGB(column, row, (value << 16) | (value << 8) | value);
            }
        }
        return image;
    }

    private static String coco(int width, int height, int x, int y, int cardWidth, int cardHeight) {
        return """
                {
                  "images": [{"id": 1, "file_name": "ignored.png", "width": %d, "height": %d}],
                  "categories": [
                    {"id": 1, "name": "document", "role": "document"},
                    {"id": 2, "name": "mark", "role": "fixed"}
                  ],
                  "annotations": [
                    {"id": 1, "image_id": 1, "category_id": 1, "bbox": [%d, %d, %d, %d]},
                    {"id": 2, "image_id": 1, "category_id": 2, "bbox": [%d, %d, 8, 8]}
                  ]
                }
                """
                .formatted(width, height, x, y, cardWidth, cardHeight, x + 6, y + 6);
    }
}
