package com.kyc.dto.documentia;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Enveloppe du banc. Les blocs absents restent {@code null} : Jackson les écrit pour que Swagger montre la forme.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DocumentIaAnalysisResponse(
        String pipeline,
        String stoppedAt,
        String provider,
        boolean providerCalled,
        UUID schemaVersionId,
        Object quality,
        Object rawModel,
        Object parsed,
        Object fields,
        Object mrz,
        Object validation,
        Object indicators,
        Object scores,
        Object decision) {

    public static DocumentIaAnalysisResponse empty() {
        return new DocumentIaAnalysisResponse(
                "vision-1",
                null,
                "vision_llm",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static DocumentIaAnalysisResponse parsed(Object parsed, java.util.UUID schemaVersionId) {
        return new DocumentIaAnalysisResponse(
                "vision-1",
                "parse",
                "vision_llm",
                false,
                schemaVersionId,
                null,
                null,
                parsed,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
