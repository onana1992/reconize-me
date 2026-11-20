package com.kyc.authenticity;

import java.awt.image.BufferedImage;
import java.util.List;

/** Gabarit chargé : spécimen, rectangle document, canevas dérivé, boîtes et cibles fixes. */
public record AuthenticityTemplate(
        String documentId,
        String code,
        String version,
        String side,
        int imageWidth,
        int imageHeight,
        int documentX,
        int documentY,
        int documentWidth,
        int documentHeight,
        int canvasWidth,
        int canvasHeight,
        double ratio,
        int fixedCount,
        int fieldCount,
        List<SourceBox> boxes,
        List<SourceBox> fixedTargets,
        List<SourceBox> positionTargets,
        BufferedImage reference) {

    /** Valeurs lues du COCO. Le canevas et le ratio sont calculés dans build. */
    static final class Spec {
        private String code;
        private String version;
        private String side;
        private int imageWidth;
        private int imageHeight;
        private int documentX;
        private int documentY;
        private int documentWidth;
        private int documentHeight;
        private int fixedCount;
        private int fieldCount;
        private List<SourceBox> boxes = List.of();
        private List<SourceBox> fixedTargets = List.of();
        private List<SourceBox> positionTargets = List.of();
        private BufferedImage reference;

        Spec withCode(String value) {
            code = value;
            return this;
        }

        Spec withVersion(String value) {
            version = value;
            return this;
        }

        Spec withSide(String value) {
            side = value;
            return this;
        }

        Spec withImageWidth(int value) {
            imageWidth = value;
            return this;
        }

        Spec withImageHeight(int value) {
            imageHeight = value;
            return this;
        }

        Spec withDocumentX(int value) {
            documentX = value;
            return this;
        }

        Spec withDocumentY(int value) {
            documentY = value;
            return this;
        }

        Spec withDocumentWidth(int value) {
            documentWidth = value;
            return this;
        }

        Spec withDocumentHeight(int value) {
            documentHeight = value;
            return this;
        }

        Spec withFixedCount(int value) {
            fixedCount = value;
            return this;
        }

        Spec withFieldCount(int value) {
            fieldCount = value;
            return this;
        }

        Spec withBoxes(List<SourceBox> value) {
            boxes = value;
            return this;
        }

        Spec withFixedTargets(List<SourceBox> value) {
            fixedTargets = value;
            return this;
        }

        Spec withPositionTargets(List<SourceBox> value) {
            positionTargets = value;
            return this;
        }

        Spec withReference(BufferedImage value) {
            reference = value;
            return this;
        }

        AuthenticityTemplate build() {
            return new AuthenticityTemplate(
                    code + ":" + version + ":" + side,
                    code,
                    version,
                    side,
                    imageWidth,
                    imageHeight,
                    documentX,
                    documentY,
                    documentWidth,
                    documentHeight,
                    documentWidth * TemplateEngine.CANVAS_SCALE,
                    documentHeight * TemplateEngine.CANVAS_SCALE,
                    documentWidth / (double) documentHeight,
                    fixedCount,
                    fieldCount,
                    boxes,
                    fixedTargets,
                    positionTargets,
                    reference);
        }
    }
}
