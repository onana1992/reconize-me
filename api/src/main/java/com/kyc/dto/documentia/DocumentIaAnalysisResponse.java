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

    public static DocumentIaAnalysisResponse quality(DocumentImageQuality quality) {
        return new DocumentIaAnalysisResponse(
                "vision-1",
                "quality",
                "vision_llm",
                false,
                null,
                quality,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static DocumentIaAnalysisResponse vision(
            DocumentImageQuality quality, Object rawModel, Object parsed, UUID schemaVersionId) {
        return new DocumentIaAnalysisResponse(
                "vision-1",
                "vision",
                "vision_llm",
                true,
                schemaVersionId,
                quality,
                rawModel,
                parsed,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static DocumentIaAnalysisResponse providerUnavailable(DocumentImageQuality quality) {
        return failedVision(quality, "provider_unavailable");
    }

    public static DocumentIaAnalysisResponse invalidModelJson(DocumentImageQuality quality) {
        return failedVision(quality, "invalid_model_json");
    }

    public static DocumentIaAnalysisResponse normalized(
            DocumentImageQuality quality,
            Object rawModel,
            Object parsed,
            UUID schemaVersionId,
            Object fields,
            Object validation,
            boolean providerCalled) {
        return new DocumentIaAnalysisResponse(
                "vision-1",
                "normalize",
                "vision_llm",
                providerCalled,
                schemaVersionId,
                quality,
                rawModel,
                parsed,
                fields,
                null,
                validation,
                null,
                null,
                null);
    }

    public static DocumentIaAnalysisResponse mrz(
            DocumentImageQuality quality,
            Object rawModel,
            Object parsed,
            UUID schemaVersionId,
            Object fields,
            Object validation,
            Object mrz,
            boolean providerCalled) {
        return new DocumentIaAnalysisResponse(
                "vision-1",
                "mrz",
                "vision_llm",
                providerCalled,
                schemaVersionId,
                quality,
                rawModel,
                parsed,
                fields,
                mrz,
                validation,
                null,
                null,
                null);
    }

    public static DocumentIaAnalysisResponse judged(
            String stoppedAt,
            DocumentImageQuality quality,
            Object rawModel,
            Object parsed,
            UUID schemaVersionId,
            Object fields,
            Object validation,
            Object mrz,
            Object indicators,
            Object scores,
            Object decision,
            boolean providerCalled) {
        return new DocumentIaAnalysisResponse(
                "vision-1",
                stoppedAt,
                "vision_llm",
                providerCalled,
                schemaVersionId,
                quality,
                rawModel,
                parsed,
                fields,
                mrz,
                validation,
                indicators,
                scores,
                decision);
    }

    public DocumentIaAnalysisResponse withDecision(Object decision) {
        return new DocumentIaAnalysisResponse(
                pipeline,
                stoppedAt,
                provider,
                providerCalled,
                schemaVersionId,
                quality,
                rawModel,
                parsed,
                fields,
                mrz,
                validation,
                indicators,
                scores,
                decision);
    }

    private static DocumentIaAnalysisResponse failedVision(DocumentImageQuality quality, String indicator) {
        return new DocumentIaAnalysisResponse(
                "vision-1",
                "vision",
                "vision_llm",
                true,
                null,
                quality,
                null,
                null,
                null,
                null,
                null,
                java.util.List.of(indicator),
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
