package com.kyc.authenticity;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Photo de spécimen. Les gabarits de test la portent déjà décodée. Ceux du catalogue la décodent à la mesure. */
final class SpecimenPicture {

    private final BufferedImage held;
    private final String folder;
    private final Path file;
    private final DecodedSpecimens decoded;
    private final int width;
    private final int height;

    private SpecimenPicture(
            BufferedImage held, String folder, Path file, DecodedSpecimens decoded, int width, int height) {
        this.held = held;
        this.folder = folder;
        this.file = file;
        this.decoded = decoded;
        this.width = width;
        this.height = height;
    }

    static SpecimenPicture held(BufferedImage image) {
        return new SpecimenPicture(image, null, null, null, 0, 0);
    }

    static SpecimenPicture lazy(String folder, Path file, DecodedSpecimens decoded, int width, int height) {
        return new SpecimenPicture(null, folder, file, decoded, width, height);
    }

    SpecimenPicture sized(int width, int height) {
        if (held != null) {
            return this;
        }
        return new SpecimenPicture(null, folder, file, decoded, width, height);
    }

    BufferedImage image() {
        if (held != null) {
            return held;
        }
        BufferedImage image = decoded.load(folder, file);
        if (image.getWidth() != width || image.getHeight() != height) {
            throw new IllegalStateException("Gabarit " + folder + " : la taille du spécimen ("
                    + image.getWidth() + "x" + image.getHeight() + ") diffère du COCO (" + width + "x" + height + ")");
        }
        return image;
    }
}
