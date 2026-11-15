package com.kyc.dto.authenticity;

import java.util.List;

public record AuthenticityTemplatesResponse(List<Template> templates) {

    public record Template(
            String documentId,
            String code,
            String version,
            String side,
            Integer imageWidth,
            Integer imageHeight,
            Integer canvasWidth,
            Integer canvasHeight,
            int fixedCount,
            int fieldCount) {}
}
